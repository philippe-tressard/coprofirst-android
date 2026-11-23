#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
"""Aucun nom de personne réelle dans ce dépôt PUBLIC (#326, `standards/14` §9).

Une LISTE BLANCHE, jamais une liste noire : écrire les vrais noms dans un contrôle les
republierait, compilés. La convention des fixtures est un prénom suivi d'un nom-étiquette
en capitales (« Camille EXEMPLE ») ; tout nom repéré doit figurer dans `FICTIFS` — l'y
ajouter, c'est AFFIRMER qu'il est inventé. Un mot repéré qui ne nomme personne (« Compte »,
« Résidence ») va dans `PAS_DES_NOMS`. Une entrée qui ne sert plus fait échouer le
contrôle. Même forme que le contrôle du serveur (`api/tests/test_identites_fictives.py`
de coprofirst).

Formes repérées :
- une civilité suivie d'un nom (« Mme Exemple », « Monsieur Camille EXEMPLE »), partout ;
- « Prénom NOM » ou « NOM Prénom » (NOM en capitales), et un prénom composé suivi d'un nom
  à casse mixte (« Jean-Hervé Exemple »), partout ;
- la valeur d'un champ de nom (`"nom": "…"`, `prenom = "…"`, `"auteur_nom" to "…"`…) dans
  les jeux de données (`src/debug`, `src/test`, `src/androidTest`, tests partagés) : chaque
  mot d'une valeur dont tous les mots ont une majuscule (« Camille Exemple ») est jugé ; une
  valeur qui contient un mot en minuscules ou un chiffre est un libellé (« Conseil syndical »).

Portée : les fichiers suivis par git sous `<module>/src/` (modules de `settings.gradle.kts`),
`ressources-communes/`, `tests-interface-communs/`, `docs/`, `README.md`, `CLAUDE.md`. Les
lignes SPDX et `LICENSES/` n'y sont pas : l'attribution légale nomme l'auteur, c'est voulu.

⚠️ Ce qu'il ne voit pas : « Prénom Nom » à casse mixte sans civilité hors d'un champ de nom,
un nom dans un identifiant, ni ce qui est public sans être un fichier — messages de commit,
tickets, PR (`docs/play-store/invitation-testeurs.md` le rappelle). Les numéros de
téléphone : aucune règle ici (#326 : une tranche à sourcer, ticket à part).

Sortie : 0 conforme, 1 refus, 2 INCONNU. `--autotest` prouve qu'il refuse et accepte.
"""

from __future__ import annotations

import pathlib
import re
import subprocess
import sys
import unicodedata

RACINE = pathlib.Path(__file__).resolve().parent.parent
SUFFIXES = (".kt", ".kts", ".xml", ".json", ".md")
PARTAGES = ("ressources-communes/", "tests-interface-communs/", "docs/")
RACINE_DOCS = ("README.md", "CLAUDE.md")
DONNEES = re.compile(r"(^|/)src/(debug|test|androidTest)/|^tests-interface-communs/")

#: Les noms inventés : les prénoms et les noms-étiquettes des fixtures. En ajouter un,
#: c'est affirmer qu'il ne désigne personne.
FICTIFS = frozenset("""
    ALIX BASILE CAMILLE CELESTE CLAUDE DESIRE DOMINIQUE ELSA FIRMIN JEAN-BAPTISTE LUCIE MARC
    MORGAN PAUL SACHA
    COMPTA DEMO ESSAI EXEMPLE FICTIF GESTION INVENTE MODELE
""".split())
#: Le titulaire du compte développeur : le magasin affiche son nom civil (standards/14 §3 bis),
#: et le guide de la fiche le dit. Ce n'est pas une donnée d'essai.
AUTEUR = frozenset("PHILIPPE TRESSARD".split())
#: Les mots que les formes attrapent sans qu'ils nomment quelqu'un.
PAS_DES_NOMS = frozenset("""
    ABC ANDROID APPELE APPELEE AUCUNE AVANT CABINET CADRE CETTE CHAQUE CLE COMPTE CONCLUSION
    CONTENU CONTROLES COTE DERNIERE DU ECOLE ECRIT ENTIERE FAQ FILE GET HTML IMPOSEE INCENDIE
    INTERCALAIRES ISO JOUR JOURS JSON KANBAN LA LARGEUR LE LES LEUR LIEU LOGE LU LUCIDE
    MARQUE-PAGE NATURE NOM NUMERO OKHTTP OUVERTE PAPIER PDF PORTE PRENOM PROTECTION REGLE RELEVE
    REPARTITION RESIDENCE RETIRE RETIRER SA SANS SDK SECURITE SERVIE SES SEUL SEULS SIGNATURE
    SON STABLE TERMITES TILLEULS TRACE TRACES UN UNE VISIONNEUSE
""".split())

_MAJ = "A-ZÀÂÄÇÉÈÊËÎÏÔÖÙÛÜ"
_MIN = "a-zàâäçéèêëîïôöùûü"
_PRENOM = rf"[{_MAJ}][{_MIN}]+(?:-[{_MAJ}][{_MIN}]+)?"
_NOM = rf"[{_MAJ}][{_MAJ}'-]+[{_MAJ}]"  # trois lettres au moins : « CS », « OS » sont des sigles
_COMPOSE = rf"[{_MAJ}][{_MIN}]+-[{_MAJ}][{_MIN}]+"
PERSONNE = re.compile(rf"(?<![\w-])(?:({_PRENOM})\s+({_NOM})|({_NOM})\s+({_PRENOM})|({_COMPOSE})\s+([{_MAJ}][\w'-]+))(?![\w-])")
CIVILITE = re.compile(rf"(?<!\w)(?:M\.|Mme|Mlle|Monsieur|Madame|Mademoiselle)\s+((?:{_PRENOM}\s+)?[{_MAJ}][\w'-]+)")
CHAMP = re.compile(r'"?\b(nom|prenom|nom_affiche|nom_complet|auteur_nom|porteur_nom|createur_nom|'
                   r'destinataire_nom|president_nom|syndic_nom|membre_nom|prenom_nom)"?\s*(?::|=|to)\s*"([^"$]*)"')


def norme(mot: str) -> str:
    sans = unicodedata.normalize("NFKD", mot).encode("ascii", "ignore").decode()
    return sans.upper().strip("'-")


def mots_d_un_nom(valeur: str) -> list[str]:
    """Les mots à juger d'une valeur de champ de nom ; aucun si c'est un libellé."""
    mots = valeur.split()
    if not mots or any(re.search(r"[0-9&()./]", m) or not m[0].isupper() for m in mots):
        return []
    return [m for m in mots if len(m) > 1]


def reperes(chemin: str, texte: str) -> list[tuple[int, str]]:
    """Les mots repérés comme noms de personne, avec leur ligne."""
    trouves = []
    donnees = bool(DONNEES.search(chemin))
    for n, ligne in enumerate(texte.splitlines(), 1):
        if "SPDX-" in ligne:
            continue
        for m in PERSONNE.finditer(ligne):
            trouves += [(n, g) for g in m.groups() if g]
        for m in CIVILITE.finditer(ligne):
            trouves += [(n, g) for g in m.group(1).split()]
        if donnees:
            for m in CHAMP.finditer(ligne):
                trouves += [(n, g) for g in mots_d_un_nom(m.group(2))]
    return trouves


def juger(fichiers: dict[str, str]) -> tuple[list[str], set[str]]:
    fautes, servis = [], set()
    for chemin, texte in fichiers.items():
        for n, mot in reperes(chemin, texte):
            cle = norme(mot)
            if cle in FICTIFS or cle in PAS_DES_NOMS or cle in AUTEUR:
                servis.add(cle)
            else:
                fautes.append(f"{chemin}:{n} : « {mot} » n'est ni un nom inventé déclaré ni un mot déclaré")
    return fautes, servis


def perimetre(suivis: list[str], modules: list[str]) -> list[str]:
    racines = tuple(f"{m}/src/" for m in modules) + PARTAGES
    return [f for f in suivis if (f.startswith(racines) and f.endswith(SUFFIXES)) or f in RACINE_DOCS]


def autotest() -> bool:
    fautifs = {
        "app/src/test/A.kt": 'val p = Personne(nom = "Martinez")',
        "app/src/debug/B.kt": '"""{"auteur_nom": "Camille Lefort"}"""',
        "docs/c.md": "Signalé par Mme Lefort.",
        "app/src/main/res/values/d.xml": "<string name=\"x\">Par exemple : Camille LEFORT</string>",
        "app/src/test/E.kt": 'assertEquals("Jean-Hervé Lefort", f("jean-hervé lefort"))',
    }
    sains = {
        "app/src/test/A.kt": 'val p = Personne(nom = "Camille EXEMPLE", prenom = "Alix")',
        "app/src/debug/B.kt": '"""{"auteur_nom": "Conseil syndical", "nom": "Ascenseurs du Parc (fictif)"}"""',
        "app/src/main/res/values/d.xml": "<string name=\"x\">Par exemple : Dominique ESSAI</string>",
        "app/src/main/java/F.kt": '// SPDX-FileCopyrightText: 2026 Prénom NOMREEL',
        "app/src/test/G.kt": 'val n = "Compte EXEMPLE"',
    }
    ok = all(juger({c: t})[0] for c, t in fautifs.items())
    ok = ok and not juger(sains)[0]
    ok = ok and perimetre(["app/src/test/A.kt", "scripts/x.py", "docs/a.md", "README.md", "LICENSES/x.txt"],
                          ["app"]) == ["app/src/test/A.kt", "docs/a.md", "README.md"]
    return ok


def main() -> int:
    if sys.argv[1:] == ["--autotest"]:
        ok = autotest()
        print("✅ autotest des identités fictives" if ok else "❌ autotest des identités fictives en échec")
        return 0 if ok else 1
    reglages = (RACINE / "settings.gradle.kts").read_text(encoding="utf-8")
    modules = [m.strip('":') for m in re.findall(r'":[a-z-]+"', reglages)]
    suivis = subprocess.run(["git", "ls-files", "--cached"], cwd=RACINE, capture_output=True,
                            text=True).stdout.splitlines()
    juges = {f: (RACINE / f).read_text(encoding="utf-8") for f in perimetre(suivis, modules) if (RACINE / f).is_file()}
    if not modules or not juges:
        print("❓ INCONNU : aucun module lu ou aucun fichier à juger")
        return 2
    fautes, servis = juger(juges)
    fautes += [f"« {m} » déclaré sans plus servir — retirer l'entrée" for m in sorted((FICTIFS | PAS_DES_NOMS | AUTEUR) - servis)]
    for faute in fautes:
        print(f"❌ {faute}")
    if fautes:
        return 1
    print(f"✅ identités : {len(juges)} fichiers, aucun nom hors des noms inventés déclarés")
    return 0


if __name__ == "__main__":
    sys.exit(main())
