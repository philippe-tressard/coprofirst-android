#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
"""Ce que le serveur SERT ne se recalcule pas dans l'app (#334) : `python3 -I scripts/test_regles_servies.py`.

Le pendant du contrôle de l'iOS (`scripts/verifier-regles-servies.py` de coprofirst-ios,
référence fonctionnelle) : on en reprend le comportement, pas le code. Une règle recopiée du
site diverge au premier arbitrage du serveur ; l'app lit le champ servi (`est_moderateur`,
un `peut_*`, `nom_affiche`, `perimetre_libelle`, `est_auteur`, `colonne_kanban`) ou une
table GÉNÉRÉE depuis la release (`OngletsDuSite.roles…`, les seuils des notations).

Chaque motif vise la COPIE, jamais le champ servi, et nomme ce qu'on lit à la place. Sont
jugés le code de l'app (`<module>/src/main/**.kt`, chaque module de `settings.gradle.kts`)
et les générateurs (`scripts/generer_*.py`), qui pourraient réécrire la règle. L'API
simulée (`src/debug`) n'est pas jugée : elle joue le serveur, et calcule donc ce qu'il sert.

Une dispense se déclare avec sa raison ; une dispense qui ne couvre plus rien échoue. Une
dispense d'ÉTAPE (la règle n'est pas encore servie) nomme le ticket qui la lèvera.
"""

from __future__ import annotations

import pathlib
import re
import subprocess
import tempfile
import unittest

RACINE = pathlib.Path(__file__).resolve().parent.parent

ROLES = r"(admin|operateur|conseil_syndical|syndic|mandataire|aidant|externe|locataire|propri[eé]taire|copropri[eé]taire\w*|r[eé]sident)"

#: (nom, motif, ce qu'on lit à la place). Une dispense nomme les motifs dont elle dispense.
RETIREES: list[tuple[str, re.Pattern[str], str]] = [
    # Un rôle ou un statut LITTÉRAL comparé pour décider d'un geste.
    ("rôle littéral", re.compile(rf"\b(roles?|statut|portes)\b[^\n]*(==|!=|\.contains\() *\"{ROLES}\""),
     "`est_moderateur` ou un `peut_*` servi ; à défaut, les populations générées (`OngletsDuSite.roles…`, `statuts…`)"),
    ("rôle littéral", re.compile(rf"\"{ROLES}\" *!?in +[\w.?]*\b(roles|portes)\b"),
     "`est_moderateur` ou un `peut_*` servi ; à défaut, les populations générées (`OngletsDuSite.roles…`)"),
    # La même chose par une constante de l'app.
    ("constante de rôle", re.compile(r"\bROLE_[A-Z_]+\b *!?in\b|(==|!=) *[\w.]*\bROLE_[A-Z_]+\b"),
     "`est_moderateur` ou un `peut_*` servi — une constante de rôle ne décide que d'une donnée affichée (dispensée)"),
    # « CS ou admin » déduit des rôles (`est_moderateur`, contrat 1.25.0, coprofirst-ios#136).
    ("CS déduit", re.compile(r"\b(cs|estConseil|conseil|moderateur|estModerateur)\b *[:=][^=\n][^\n]*\b(roles|portes|role)\b"),
     "`est_moderateur` servi (`DroitsOnglet.cs`)"),
    # Un `peut_*` recalculé depuis ce que le compte est.
    ("peut recalculé", re.compile(r"\bpeut[A-Z]\w*\s*(:\s*Boolean\s*)?(get\(\)\s*)?=[^=\n][^\n]*\b(roles?|statut|estModerateur|cs|outilDuConseil|administrateur)\b"),
     "le `peut_*` servi par l'objet ou ses gestes (`GET …/gestes`)"),
    # « Prénom NOM » est SERVI (`nom_affiche`…, contrat 1.25.0) : jamais accolé dans l'app.
    ("nom accolé", re.compile(r"\$\{?[\w.?]*\b[pP]renom\w*\}?[^\"\n]{0,3}\$\{?[\w.?]*\b[nN]om\b|\b[pP]renom\w*\s*\+\s*\" \""),
     "le nom servi (`nom_affiche`…) par `FormatsDuSite.nomServi`, jamais un prénom et un nom accolés"),
    # Le plancher sert « Prénom NOM » partout (`nom_affiche`, `locataire_nom_affiche`, `bailleur_nom_affiche`,
    # `nom_aide_affiche`, `auteur_affiche`) : le nom servi SEUL. Le repli prénom-nom de `FormatsDuSite.nom`
    # n'est permis qu'à l'annuaire, comme l'iOS (`nom(affiche:prenom:nom:)`, #356).
    ("repli du nom", re.compile(r"\bFormatsDuSite\.nom\("),
     "le nom servi seul par `FormatsDuSite.nomServi` — le repli prénom-nom est réservé à l'annuaire (dispensé)"),
    # Le lieu d'un contenu est SERVI (`perimetre_libelle`, contrat 1.24.0) : la règle des chemins « › » ne se recopie pas.
    ("lieu recomposé", re.compile(r"\" › \""),
     "le lieu servi (`perimetre_libelle`) par `LieuServi.lieu`"),
    # « Le mien » est SERVI (`est_auteur`, contrat 1.26.0).
    ("le mien", re.compile(r"\bauteurId\s*==|==\s*[\w.?]*\bauteurId\b"),
     "`est_auteur` servi"),
    # L'état d'une moyenne se lit par les seuils GÉNÉRÉS du site.
    ("seuil de moyenne", re.compile(r"\bmoyenne\w*\s*[<>]=?\s*[0-9]"),
     "les seuils générés des notations (tables-clients.json)"),
    # La colonne d'une carte et la clôture d'une affaire sont SERVIES (`colonne_kanban`, `close`, référentiels).
    ("Kanban", re.compile(r"\b(colonneParStatut|statutParColonne|statutsClos|COLONNE_PAR_STATUT_TICKET|STATUTS_TICKET_CLOS)\b"),
     "la colonne et la clôture servies (`colonne_kanban`, `close`, `GET /tickets/referentiels`)"),
    # Qui voit quelle page : la règle du menu du site, écrite à la main (une branche qui
    # ouvre un identifiant de page selon ce que le compte est).
    ("menu recopié", re.compile(r"^\s*\"[a-z-]+\"(\s*,\s*\"[a-z-]+\")*\s*->[^\n]*\bdroits\."),
     "la règle du menu servie ou publiée par coprofirst"),
    # Le prix d'une annonce est SERVI en centimes entiers (`prix_centimes`, contrat 2.13.0, #370) :
    # le flottant `prix` d'une annonce lue ne s'affiche ni ne se trie plus (l'envoi, lui, reste `prix`).
    ("prix flottant", re.compile(r"\.prix\?"),
     "`prixCentimes` servi, rendu par `FormatsDuSite.montant`"),
    # La part d'une option de sondage est SERVIE, arrondie par l'API (`options[].part`, contrat 2.14.0,
    # coprofirst#3060, #380) : aucun pourcentage ne se recalcule depuis les votes.
    ("pourcentage recalculé", re.compile(r"\bpourcentage\s*\(|\*\s*100\)?\s*\.roundToInt\(\)|/\s*\w*[tT]otal\w*\s*\*\s*100|\*\s*100\s*/\s*\w*[tT]otal"),
     "la part servie (`OptionSondage.part`, `options[].part`)"),
]

#: chemin → (les motifs dispensés, par leur nom ; la raison). Une dispense ne couvre QUE ses
#: motifs : un autre motif reste refusé dans le fichier dispensé.
DISPENSES: dict[str, tuple[frozenset[str], str]] = {
    "app/src/main/java/fr/coprofirst/app/fonctions/admin/ModelesUtilisateurs.kt": (frozenset({"constante de rôle"}),
        "les rôles d'un compte LISTÉ dans l'écran des utilisateurs : une donnée affichée, pas un droit du lecteur"),
    # Les pendants des deux dispenses de l'iOS (`ServiceAnnuaire.swift`, `ServiceAnnuaireCS.swift`).
    "app/src/main/java/fr/coprofirst/app/commun/annuaire/Annuaire.kt": (frozenset({"repli du nom"}),
        "la carte de l'annuaire rend aussi la fiche du conseil ou du syndic pas encore enregistrée, ou "
        "renommée (`enCarte`, le nom servi remis à `null` à la saisie) : sans nom servi, le prénom et le nom saisis"),
    "app/src/main/java/fr/coprofirst/app/fonctions/espacecs/ServiceAnnuaireCS.kt": (frozenset({"repli du nom"}),
        "une fiche du conseil ou du syndic pas encore enregistrée, ou renommée, n'a pas de nom servi : "
        "le nom saisi (la feuille, elle, montre le nom que le serveur rend de la saisie, `POST /noms/affiche`)"),
    "app/src/main/java/fr/coprofirst/app/fonctions/admin/EtatUtilisateurs.kt": (frozenset({"constante de rôle"}),
        "l'abréviation « CS » d'un rôle affiché dans la liste des comptes"),
    "app/src/main/java/fr/coprofirst/app/commun/perimetres/ArbreDesPerimetres.kt": (frozenset({"lieu recomposé"}),
        "le libellé du CHOIX en cours dans le sélecteur de périmètres (« Bâtiment 1 › Hall ») : "
        "une sélection locale, que rien ne sert — le lieu d'un contenu, lui, se lit (`LieuServi`)"),
    "app/src/main/java/fr/coprofirst/app/fonctions/admin/FicheUtilisateur.kt": (frozenset({"constante de rôle"}),
        "la teinte d'un rôle affiché sur la fiche d'un compte"),
}


def juges(racine: pathlib.Path) -> list[str]:
    """Les fichiers jugés : le code de chaque module du build, et les générateurs."""
    suivis = subprocess.run(["git", "ls-files", "--cached"], cwd=racine, capture_output=True,
                            text=True, check=True).stdout.splitlines()
    modules = re.findall(r'":([a-z-]+)"', (racine / "settings.gradle.kts").read_text(encoding="utf-8"))
    if not suivis or not modules:
        raise AssertionError("INCONNU : aucun fichier suivi ou aucun module lu")
    code = re.compile(rf"^({'|'.join(map(re.escape, modules))})/src/main/.*\.kt$|^scripts/generer_[a-z_]+\.py$")
    return [f for f in suivis if code.search(f)]


def juger(racine: pathlib.Path, fichiers: list[str], dispenses: dict[str, tuple[frozenset[str], str]]) -> list[str]:
    refus, utiles = [], set()
    for chemin in fichiers:
        for n, ligne in enumerate((racine / chemin).read_text(encoding="utf-8").splitlines(), 1):
            for nom, motif, remede in RETIREES:
                m = motif.search(ligne)
                if not m:
                    continue
                if nom in dispenses.get(chemin, (frozenset(), ""))[0]:
                    utiles.add((chemin, nom))
                    continue
                refus.append(f"{chemin}:{n} : règle servie recopiée ({nom} : {m.group(0)}) — lire {remede}")
    refus += [f"{c} : dispensé de « {nom} » sans plus rien couvrir — retirer la dispense"
              for c, (noms, _) in sorted(dispenses.items()) for nom in sorted(noms) if (c, nom) not in utiles]
    return refus


FAUTIFS = [
    '    val conseil = roles.contains("conseil_syndical")',
    '        if (moi.statut == "locataire") return false',
    '    val admin = "admin" in moi.roles',
    '    val gestionnaire = "syndic" !in portes',
    '    val cs = UtilisateurAdmin.ROLE_CONSEIL in roles',
    '        if (role == ROLE_ADMIN) montrer()',
    '    val cs = portes.any { it in setOf(x) } || roles.isNotEmpty()',
    '    val moderateur: Boolean get() = role in rolesDuConseil',
    '    val peutRepondre: Boolean get() = droits.cs || estAuteur',
    '    val peutArchiver = statut != null && administrateur',
    '        Text("$prenom $nom")',
    '    val nom = "${compte.prenom} ${compte.nom.uppercase()}"',
    '    val nom = prenom + " " + nom',
    '    val lieu = chemin.joinToString(" › ")',
    '        deMoi = message.auteurId == moi.id,',
    '        if (avis.moyenne < 2.5) Etat.Mauvais',
    '    val colonne = TablesAffaires.colonneParStatut[statut]',
    '    STATUTS_TICKET_CLOS = table_simple(texte, "STATUTS_TICKET_CLOS")',
    '        "prestataires", "espace-cs" -> droits.outilDuConseil',
    '        "admin" -> droits.administrateur || droits.operateur',
    '    val nomAffiche: String get() = FormatsDuSite.nom(nomServi, prenom, nom)',
    '                FormatsDuSite.nom(compte.nomAffiche, compte.prenom, compte.nom),',
    '        annonce.prix?.let { prix ->',
    '            TRI_PRIX_CROISSANT -> retenues.sortedBy { it.prix?.toDouble() ?: Double.MAX_VALUE }',
    '    val pourcentage = CalculsSondage.pourcentage(votes, total)',
    '    fun pourcentage(votes: Int, total: Int): Int = if (total == 0) 0 else (votes.toDouble() / total * 100).roundToInt()',
    '    val part = votes * 100 / total',
]
SAINS = [
    '                cs = estModerateur == true,',
    '                administrateur = portes.any { it in OngletsDuSite.rolesAdministrateur },',
    '                locataire = s in OngletsDuSite.statutsLocataire,',
    '        TablesCommunes.rolesMisEnAvant.filter { it in roles }.forEach { pastilles += role(it) }',
    '    val loue: Boolean get() = typeLien == "locataire"',
    '    val peutRepondre: Boolean get() = servi.peutRepondre ?: false',
    '    val nomAffiche: String get() = FormatsDuSite.nomServi(nomServi)',
    '    nomBailleur = FormatsDuSite.nomServi(bailleurNomAffiche),',
    '                lieu = LieuServi.lieu(affaire?.texte("perimetre_libelle")),',
    '        deMoi = message.estAuteur ?: false,',
    '    fun masque(auteurId: Int) = "masque.$auteurId"',
    '        TablesNotations.seuilsDeMoyenne.first { moyenne < it.borne }',
    '            colonne = ticket.colonneKanban,',
    '    internal fun titre(page: PageDuSommaire): String = when (page) {',
    '        "kanban" -> KanbanAffaires(contexte)',
    '        return tete.libelle + " · " + retenus.size',
    '        annonce.prixCentimes?.let { centimes ->',
    '            prix = if (vente) saisie.prixLu?.let(BigDecimal::valueOf) else null,',
    '                saisie.prix,',
    '    val pourcentage = option.part ?: 0',
    '                stringResource(R.string.sondage_pourcentage, FormatsDuSite.nombre(pourcentage)),',
    '        if (coupees) remplissage.snapTo(pourcentage / 100f) else remplissage.animateTo(pourcentage / 100f, tween(600))',
]


class ReglesServies(unittest.TestCase):
    def test_autotest_refuse_les_copies(self):
        rates = [f for f in FAUTIFS if not any(m.search(f) for _, m, _ in RETIREES)]
        self.assertEqual(rates, [], "copies non refusées")

    def test_autotest_accepte_les_lectures_servies(self):
        refuses = [s for s in SAINS if any(m.search(s) for _, m, _ in RETIREES)]
        self.assertEqual(refuses, [], "lectures servies refusées")

    def test_autotest_dispense_sans_objet(self):
        with tempfile.TemporaryDirectory() as d:
            racine = pathlib.Path(d)
            (racine / "a.kt").write_text("val x = 1\n", encoding="utf-8")
            (racine / "b.kt").write_text('val c = roles.contains("admin")\n', encoding="utf-8")
            self.assertEqual(len(juger(racine, ["a.kt", "b.kt"], {"a.kt": (frozenset({"rôle littéral"}), "r")})), 2)
            self.assertEqual(juger(racine, ["a.kt", "b.kt"], {"b.kt": (frozenset({"rôle littéral"}), "r")}), [])
            # Une dispense ne couvre que ses motifs : le menu dispensé ne couvre pas un rôle littéral.
            self.assertEqual(len(juger(racine, ["a.kt", "b.kt"], {"b.kt": (frozenset({"menu recopié"}), "r")})), 2)

    def test_le_depot(self):
        fichiers = juges(RACINE)
        self.assertGreater(len(fichiers), 100, "INCONNU : trop peu de fichiers jugés")
        self.assertEqual(juger(RACINE, fichiers, DISPENSES), [])


if __name__ == "__main__":
    unittest.main()
