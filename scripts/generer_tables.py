#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
"""Les TABLES du site que l'app lit, générées au build depuis `contrat/tables-clients.json`
(la pièce jointe de la release de coprofirst au plancher) — comme l'iOS
(`scripts/generateurs/onglets.py`) :

- `PagesParDefaut` : les pages de navigation (`pages`, celles qui ont un `href`), dans
  l'ordre par défaut, et le titre du fil (`titreFil`) ; celles qui n'ont pas d'entrée de
  menu (`horsMenu` : la page des notifications, que la cloche ouvre) ;
- `OngletsDuSite` : les onglets de chaque page et leur réserve, leur descriptif et leur
  adresse sur le site (« Sur le site »), les pages réservées à l'administration, qui
  porte chaque droit (`rolesDuDroit`, `statutsDuProfil`) ;
- `AvisDuSite` (paquet `commun`) : le temps que reste à l'écran l'avis qui suit un geste
  (`dureeToastMs`), comme l'iOS (`scripts/generateurs/avis.py`) ;
- `BalisesDuSite` (paquet `commun.texte`) : la liste blanche du texte riche — balises et
  attributs admis (`balisesAdmises`, `attributsAdmis`), schémas d'un lien touchable —
  comme l'iOS (`scripts/generateurs/charte.py`, `balises`). Sa forme s'écrit ici
  (`kotlin_balises`) ; elle se GÉNÈRE dans `:partage-android` (`generer_balises.py`, #206) ;
- `TablesCommunes` (paquet `commun`) : le libellé et la pastille de chaque statut et de
  chaque rôle (`attributsStatut`, `attributsRole`, `libellesHerites`), les rôles mis en
  avant (`rolesMisEnAvant`), les statuts qu'un compte peut demander
  (`statutsDemandables`), les options du thème de l'appareil (`optionsTheme`,
  `themeParDefaut`, `cookieTheme`) ;
- `Separateur` (paquet `commun.texte`) : les trois séparateurs du site (`separateurs`,
  `SEPARATEUR` de front/src/lib/separateurs.ts) — une espace sécable avant, insécable
  après : la ligne se coupe devant, jamais après (#117), comme l'iOS
  (`generateurs/separateurs.py`) ;
- `NaturesDuFil` et `NaturesAffaire` (paquet `charte`) : ce que le fil montre de chaque
  type d'élément (`naturesDuFil` : libellé, teinte, pictogramme) et les natures d'une
  affaire (`naturesAffaire`, teinte `teinteNature`), comme l'iOS
  (`scripts/generateurs/charte.py`, `natures_du_fil` ; `affaires.py`, `natures`). Une
  teinte `var(--…)` devient le jeton de `Couleurs` qui la porte (`Couleurs::filRouge`),
  jamais une couleur : le nom de la propriété et la liste des jetons portés se lisent
  dans `generer_charte.py`, écrits une fois ;
- `TablesAcces` (paquet `commun.acces`) : l'état d'un badge ou d'une télécommande
  (`statutsAcces`) et les deux familles d'accès (`typesAcces`, `types-acces.ts`), que
  « Mes lots & accès » et l'Espace CS lisent tous deux (#79) — comme l'iOS
  (`scripts/generateurs/acces_prestataires.py`).

Une table qui change de forme, une réserve inconnue : la génération s'arrête — l'app
doit l'apprendre, pas la deviner.

Usage : generer_tables.py <tables-clients.json> <dossier de sortie>
"""

from __future__ import annotations

import importlib.util
import json
import pathlib
import re
import sys

PAQUET = "fr.coprofirst.app.navigation"
PAQUET_TEXTE = "fr.coprofirst.app.commun.texte"
PAQUET_CHARTE = "fr.coprofirst.app.charte"
JETON = re.compile(r"^var\(--([a-z][a-z0-9-]*)\)$")
# La couleur d'état d'une nature (`--nature-fort: var(--color-…)`) → la classe `badge-*` qui la
# porte (`teintes` de PastilleTeinte.kt) — la table de l'iOS (`BADGE_PAR_COULEUR`, affaires.py).
BADGE_PAR_COULEUR = {"info": "badge-blue", "violet": "badge-purple", "orange": "badge-orange",
                     "success": "badge-green", "danger": "badge-red"}
NOM_DE_BALISE = re.compile(r"^[a-z][a-z0-9-]*$")
RESERVES = {"proprioOuCS": "ProprioOuCS", "nonLocataire": "NonLocataire", "operateur": "Operateur",
            "nonGestionnaire": "NonGestionnaire"}


class TableInattendue(Exception):
    """Une table publiée n'a pas la forme attendue."""


def chaine(texte) -> str:
    """Un littéral de chaîne Kotlin."""
    t = str(texte).replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$").replace("\n", "\\n")
    return f'"{t}"'


def sans_picto(libelle) -> str:
    """Un libellé du site sans l'émoji qui le précède."""
    return re.sub(r"^[^\w«(]+", "", str(libelle or "")).strip()


def valeur(tables: dict, nom: str):
    """La valeur d'une table publiée ; absente, la génération s'arrête."""
    v = tables.get(nom, {}).get("valeur")
    if v is None:
        raise TableInattendue(f"{nom} absente")
    return v


def texte(entree: dict, cle: str, table: str) -> str:
    """Un champ texte d'une entrée de table ; manquant, la génération s'arrête."""
    v = entree.get(cle)
    if not isinstance(v, str):
        raise TableInattendue(f"{table} : {cle} manquant ({entree})")
    return v


def teintes(nom: str, entrees: list[tuple[str, str, str]], doc: str) -> list[str]:
    """Une table Kotlin code → `LibelleTeinte` (libellé, classe `badge-*`)."""
    return [f"    /** {doc} */", f"    val {nom}: Map<String, LibelleTeinte> = mapOf("] + [
        f"        {chaine(c)} to LibelleTeinte({chaine(l)}, {chaine(b)})," for c, l, b in entrees] + ["    )", ""]


def liste(tables: dict, table: str, cle: str) -> list[str]:
    valeur = tables.get(table, {}).get("valeur", {}).get(cle)
    if not isinstance(valeur, list) or not valeur or not all(isinstance(v, str) for v in valeur):
        raise TableInattendue(f"{table}.{cle} n'est pas une liste de chaînes non vide")
    return valeur


def ensemble(valeurs: list[str]) -> str:
    return "setOf(" + ", ".join(chaine(v) for v in sorted(valeurs)) + ")"


def kotlin(tables: dict, source: str) -> str:
    pages = tables.get("pages", {}).get("valeur")
    if not isinstance(pages, list):
        raise TableInattendue("pages n'est pas une liste")
    titre = tables.get("titreFil", {}).get("valeur")
    if not isinstance(titre, str) or not titre.strip():
        raise TableInattendue("titreFil n'est pas un titre")
    l = [
        "// GÉNÉRÉ par scripts/generer_tables.py — NE PAS MODIFIER À LA MAIN.",
        f"// Source : {source} (pages, titreFil, rolesDuDroit, statutsDuProfil).",
        f"package {PAQUET}",
        "",
        "/** Les pages de navigation du site, dans l'ordre par défaut ; `pages_order` le remplace. */",
        "object PagesParDefaut {",
        "    val toutes: List<PageDeNavigation> = listOf(",
    ]
    hors_menu = []
    for p in pages:
        for cle in ("id", "navLabel", "titre", "icone"):
            if not isinstance(p.get(cle), str):
                raise TableInattendue(f"page {p.get('id')} : {cle} manquant")
        if p.get("href") is None:
            hors_menu.append(p)  # sans entrée de navigation : ni ordonnée, ni dans la barre
            continue
        if not isinstance(p["href"], str):
            raise TableInattendue(f"page {p['id']} : href inattendu")
        # Le public de la page (#333) : une page du menu sans lui ne s'afficherait jamais.
        if not isinstance(p.get("acces"), str) or not p["acces"]:
            raise TableInattendue(f"page {p['id']} : acces manquant — une page du menu a son public")
        l.append(f"        {page_kotlin(p, p['href'], p['acces'])},")
    l += ["    )", "",
          "    /** Les pages sans entrée de menu (`href` nul, route vide) : leur écran s'ouvre d'ailleurs (la cloche). */",
          "    val horsMenu: List<PageDeNavigation> = listOf(",
          *[f"        {page_kotlin(p, '')}," for p in hors_menu], "    )", "",
          "    /** Le titre de la section du fil d'activité (`.titre-fil`). */",
          f"    const val TITRE_DU_FIL: String = {chaine(titre.strip())}", "}", "",
          "/** Les onglets de chaque page du site, dans leur ordre, avec leur réserve ; qui porte chaque droit. */",
          "object OngletsDuSite {", "    val parPage: Map<String, List<OngletDuSite>> = mapOf("]
    reservees, descriptifs, routes = [], [], []
    for p in pages:
        if p.get("reserveeAdmin"):
            reservees.append(p["id"])
        onglets = p.get("onglets") or []
        if not onglets:
            continue
        l.append(f"        {chaine(p['id'])} to listOf(")
        descriptifs.append(f"        {chaine(p['id'])} to mapOf(")
        routes.append(f"        {chaine(p['id'])} to mapOf(")
        for o in onglets:
            for cle, lignes in (("descriptif", descriptifs), ("route", routes)):
                valeur = o.get(cle)
                if valeur is not None and not isinstance(valeur, str):
                    raise TableInattendue(f"onglet {p['id']}/{o.get('id')} : {cle} inattendu")
                if valeur:
                    lignes.append(f"            {chaine(o['id'])} to {chaine(valeur)},")
            r = o.get("reserve")
            if r is not None and r not in RESERVES:
                raise TableInattendue(f"réserve inconnue « {r} » ({p['id']}/{o.get('id')}) — l'app doit l'apprendre")
            reserve = f"ReserveOnglet.{RESERVES[r]}" if r else "null"
            l.append(f"            OngletDuSite({chaine(o['id'])}, {chaine(sans_picto(o.get('label')))}, {reserve}, "
                     f"{'true' if o.get('ouvertOperateur') else 'false'}),")
        l.append("        ),")
        descriptifs.append("        ),")
        routes.append("        ),")
    l += ["    )", "",
          "    /** Le descriptif de chaque onglet (page → onglet → texte du site, à passer par `TexteDuSite.apercu`). */",
          "    val descriptifs: Map<String, Map<String, String>> = mapOf(", *descriptifs, "    )", "",
          "    /** L'adresse de chaque onglet sur le site (page → onglet → `/admin?onglet=…`), sous celle de la résidence. */",
          "    val routes: Map<String, Map<String, String>> = mapOf(", *routes, "    )", "",
          f"    val pagesReserveesAdmin: Set<String> = {ensemble(reservees)}",
          f"    val rolesAdministrateur: Set<String> = {ensemble(liste(tables, 'rolesDuDroit', 'admin'))}",
          f"    val rolesOperateur: Set<String> = {ensemble(liste(tables, 'rolesDuDroit', 'operateur'))}",
          f"    val rolesProprietaire: Set<String> = {ensemble(liste(tables, 'rolesDuDroit', 'proprietaire'))}",
          f"    val statutsLocataire: Set<String> = {ensemble(liste(tables, 'statutsDuProfil', 'locataire'))}",
          f"    val statutsGestionnaire: Set<String> = {ensemble(liste(tables, 'statutsDuProfil', 'gestionnaire'))}",
          "}", ""]
    return "\n".join(l)


def noms(tables: dict, table: str, minimum: int) -> list[str]:
    """Une liste publiée de noms de balise ou d'attribut : en minuscules, sans doublon."""
    valeur = tables.get(table, {}).get("valeur")
    if (not isinstance(valeur, list) or len(valeur) < minimum or len(set(valeur)) != len(valeur)
            or not all(isinstance(v, str) and NOM_DE_BALISE.match(v) for v in valeur)):
        raise TableInattendue(f"{table} n'est pas une liste d'au moins {minimum} noms en minuscules, sans doublon")
    return valeur


def kotlin_balises(tables: dict, source: str) -> str:
    """`BalisesDuSite` : ce que le texte riche de l'app rend, et rien d'autre."""
    balises, attributs = noms(tables, "balisesAdmises", 10), noms(tables, "attributsAdmis", 1)
    if "href" not in attributs:
        raise TableInattendue("attributsAdmis sans href — un lien ne se lirait plus")
    return "\n".join([
        "// GÉNÉRÉ par scripts/generer_tables.py — NE PAS MODIFIER À LA MAIN.",
        f"// Source : {source} (balisesAdmises, attributsAdmis) ; schémas : ConstantesDuServeur.",
        f"package {PAQUET_TEXTE}",
        "",
        "/** La liste blanche du texte riche du site : ce que l'app rend, et rien d'autre. */",
        "object BalisesDuSite {",
        f"    val balises: Set<String> = {ensemble(balises)}",
        f"    val attributs: Set<String> = {ensemble(attributs)}",
        "}",
        "",
    ])


def attributs(tables: dict, table: str) -> dict:
    valeur = tables.get(table, {}).get("valeur")
    if not isinstance(valeur, dict) or not valeur:
        raise TableInattendue(f"{table} n'est pas une table non vide")
    for cle, a in valeur.items():
        if not isinstance(a, dict) or not isinstance(a.get("libelle"), str) or not isinstance(a.get("badge"), str):
            raise TableInattendue(f"{table}.{cle} : libelle ou badge manquant")
    return valeur


def cles_connues(tables: dict, table: str, connues: dict) -> list[str]:
    """Une liste publiée de clés, chacune connue de sa table de libellés."""
    valeur = tables.get(table, {}).get("valeur")
    if not isinstance(valeur, list) or not valeur or not all(isinstance(v, str) and v in connues for v in valeur):
        raise TableInattendue(f"{table} n'est pas une liste de clés connues")
    return valeur


def liste_kotlin(valeurs: list[str]) -> str:
    return "listOf(" + ", ".join(chaine(v) for v in valeurs) + ")"


def page_kotlin(p: dict, route: str, acces: str | None = None) -> str:
    public = f", {chaine(acces)}" if acces else ""
    return (f"PageDeNavigation({chaine(p['id'])}, {chaine(route)}, {chaine(p['navLabel'])}, "
            f"{chaine(p['titre'])}, {chaine(p['icone'])}{public})")


def kotlin_avis(tables: dict, source: str) -> str:
    millisecondes = tables.get("dureeToastMs", {}).get("valeur")
    if isinstance(millisecondes, bool) or not isinstance(millisecondes, int) or millisecondes <= 0:
        raise TableInattendue(f"dureeToastMs n'est pas une durée en millisecondes : {millisecondes!r}")
    return "\n".join([
        "// GÉNÉRÉ par scripts/generer_tables.py — NE PAS MODIFIER À LA MAIN.",
        f"// Source : {source} (dureeToastMs : `DUREE_TOAST_MS` de front/src/lib/toast.ts).",
        "package fr.coprofirst.app.commun", "",
        "/** L'avis qui suit un geste, comme le site le montre. */",
        "object AvisDuSite {",
        "    /** Le temps qu'il reste à l'écran (`DUREE_TOAST_MS` du site), en millisecondes. */",
        f"    const val DUREE_MS: Long = {millisecondes}L", "}", ""])


def kotlin_commun(tables: dict, source: str) -> str:
    statuts, roles = attributs(tables, "attributsStatut"), attributs(tables, "attributsRole")
    herites = tables.get("libellesHerites", {}).get("valeur")
    options = tables.get("optionsTheme", {}).get("valeur")
    defaut = tables.get("themeParDefaut", {}).get("valeur")
    cle = tables.get("cookieTheme", {}).get("valeur")
    if not isinstance(herites, dict) or not all(isinstance(v, str) for v in herites.values()):
        raise TableInattendue("libellesHerites n'est pas une table de libellés")
    if not isinstance(options, list) or not all(isinstance(o.get("val"), str) and isinstance(o.get("label"), str) for o in options):
        raise TableInattendue("optionsTheme n'est pas une liste d'options")
    if defaut not in [o["val"] for o in options] or not isinstance(cle, str):
        raise TableInattendue("themeParDefaut ou cookieTheme inattendu")

    def table(nom: str, valeur: dict) -> list[str]:
        lignes = [f"    val {nom}: Map<String, AttributRole> = mapOf("]
        for k, a in valeur.items():
            abrege = chaine(a["abrege"]) if isinstance(a.get("abrege"), str) else "null"
            lignes.append(f"        {chaine(k)} to AttributRole({chaine(a['libelle'])}, {chaine(a['badge'])}, {abrege}),")
        return lignes + ["    )"]

    l = ["// GÉNÉRÉ par scripts/generer_tables.py — NE PAS MODIFIER À LA MAIN.",
         f"// Source : {source} (attributsStatut, attributsRole, rolesMisEnAvant, statutsDemandables, libellesHerites, optionsTheme, themeParDefaut, cookieTheme).",
         "package fr.coprofirst.app.commun", "",
         "/** Les tables du site que lisent plusieurs écrans : statuts, rôles, thème de l'appareil. */",
         "object TablesCommunes {", *table("statuts", statuts), *table("roles", roles),
         "    /** Les rôles que l'en-tête ajoute au statut, dans cet ordre (`ROLES_MIS_EN_AVANT`). */",
         f"    val rolesMisEnAvant: List<String> = {liste_kotlin(cles_connues(tables, 'rolesMisEnAvant', roles))}",
         "    /** Les statuts qu'un compte peut demander (`STATUTS_DEMANDABLES`). */",
         f"    val statutsDemandables: List<String> = {liste_kotlin(cles_connues(tables, 'statutsDemandables', statuts))}",
         "    val libellesHerites: Map<String, String> = mapOf(",
         *[f"        {chaine(k)} to {chaine(v)}," for k, v in herites.items()], "    )", "",
         "    val optionsTheme: List<OptionTheme> = listOf(",
         *[f"        OptionTheme({chaine(o['val'])}, {chaine(o['label'])}, {chaine(o.get('desc') or '')})," for o in options],
         "    )", f"    const val THEME_PAR_DEFAUT: String = {chaine(defaut)}",
         f"    const val CLE_THEME: String = {chaine(cle)}", "}", ""]
    return "\n".join(l)


def _charte():
    """`generer_charte.py`, voisin : chargé par son chemin (`python3 -I` ne met pas le dossier
    du script dans `sys.path`), pour ne réécrire ni `propriete` ni la liste des jetons portés."""
    spec = importlib.util.spec_from_file_location("generer_charte", pathlib.Path(__file__).with_name("generer_charte.py"))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def jetons_portes(tables: dict) -> tuple[set[str], object]:
    """Les jetons que `Couleurs` porte (ceux que `generer_charte.py` garde), et ce module."""
    gc = _charte()
    try:
        palettes = gc.cascade(tables)
    except (KeyError, TypeError, AttributeError, ValueError) as e:
        raise TableInattendue(f"jetons de la charte illisibles ({e})") from e
    gardes, _ = gc.portables(palettes[gc.CHARTE][0], palettes)
    return set(gardes), gc


def kotlin_natures(tables: dict, source: str) -> str:
    """`NaturesDuFil` et `NaturesAffaire` : libellés, jetons de teinte, pictogrammes, pastilles."""
    portes, gc = jetons_portes(tables)

    def jeton(valeur, ou: str) -> str:
        m = JETON.match(str(valeur or ""))
        if not m or m.group(1) not in portes:
            raise TableInattendue(f"{ou} : la teinte {valeur!r} n'est pas un jeton de Couleurs")
        return f"Couleurs::{gc.propriete(m.group(1))}"

    rubriques = tables.get("naturesDuFil", {}).get("valeur")
    if not isinstance(rubriques, dict) or not rubriques:
        raise TableInattendue("naturesDuFil n'est pas une table non vide")
    lignes = []
    for type_, n in rubriques.items():
        if not isinstance(n, dict) or not all(isinstance(n.get(c), str) and n[c].strip() for c in ("libelle", "icone")):
            raise TableInattendue(f"naturesDuFil.{type_} : libelle ou icone manquant")
        ou = f"naturesDuFil.{type_}"
        lignes.append(f"        {chaine(type_)} to NatureDuFil({chaine(n['libelle'])}, {jeton(n.get('couleur'), ou)}, "
                      f"{jeton(n.get('fond'), ou)}, {chaine(n['icone'])}),")

    natures = tables.get("naturesAffaire", {}).get("valeur")
    teintes = tables.get("teinteNature", {}).get("valeur")
    if not isinstance(natures, list) or not natures or not isinstance(teintes, dict):
        raise TableInattendue("naturesAffaire ou teinteNature n'a pas la forme attendue")
    affaires = []
    for n in natures:
        if not isinstance(n, dict) or not all(isinstance(n.get(c), str) and n[c].strip() for c in ("val", "libelle", "icone")):
            raise TableInattendue(f"naturesAffaire : {n!r} n'a pas la forme attendue")
        teinte = teintes.get(n["val"])
        fort = JETON.match(str(teinte.get("--nature-fort", ""))) if isinstance(teinte, dict) else None
        couleur = fort.group(1).removeprefix("color-") if fort else None
        if couleur not in BADGE_PAR_COULEUR:
            raise TableInattendue(f"teinteNature.{n['val']} : pas de classe badge-* connue — l'app doit l'apprendre")
        affaires.append(f"        NatureAffaire({chaine(n['val'])}, {chaine(n['libelle'])}, {chaine(n['icone'])}, "
                        f"{chaine(BADGE_PAR_COULEUR[couleur])}),")
    return "\n".join([
        "// GÉNÉRÉ par scripts/generer_tables.py — NE PAS MODIFIER À LA MAIN.",
        f"// Source : {source} (naturesDuFil, naturesAffaire, teinteNature) ; jetons : generer_charte.py.",
        f"package {PAQUET_CHARTE}",
        "",
        "/** Ce que le fil montre de chaque type d'élément (`NATURES_DU_FIL` du site) : libellé, teinte, pictogramme. */",
        "object NaturesDuFil {",
        "    val parType: Map<String, NatureDuFil> = mapOf(", *lignes, "    )", "}",
        "",
        "/** Les natures d'une affaire, dans l'ordre du site (`NATURES`), et la pastille de leur teinte (`[data-nature]`). */",
        "object NaturesAffaire {",
        "    val toutes: List<NatureAffaire> = listOf(", *affaires, "    )", "}",
        "",
    ])


def kotlin_separateurs(tables: dict, source: str) -> str:
    """`Separateur` : point, tiret, flèche, tels que le site les écrit."""
    valeur = tables.get("separateurs", {}).get("valeur")
    noms = ("point", "tiret", "fleche")
    if not isinstance(valeur, dict) or set(valeur) != set(noms):
        raise TableInattendue(f"separateurs : forme inattendue ({valeur!r})")
    for nom in noms:
        v = valeur[nom]
        if not (isinstance(v, str) and len(v) == 3 and v[0] == " " and v[2] == "\u00a0" and not v[1].isspace()):
            raise TableInattendue(f"separateurs.{nom} : ni espace sécable avant, ni insécable après ({v!r})")
    # L'espace insécable s'écrit échappée : invisible, elle se perdrait à la relecture.
    def litteral(v: str) -> str:
        return chaine(v).replace("\u00a0", "\\u00A0")
    return "\n".join([
        "// GÉNÉRÉ par scripts/generer_tables.py — NE PAS MODIFIER À LA MAIN.",
        f"// Source : {source} (separateurs).",
        f"package {PAQUET_TEXTE}",
        "",
        "/**",
        " * Les séparateurs du site (`SEPARATEUR`) : une espace sécable avant, insécable après —",
        " * la ligne se coupe devant le séparateur, qui ouvre la suivante, jamais après.",
        " */",
        "object Separateur {",
        f"    const val point = {litteral(valeur['point'])}",
        f"    const val tiret = {litteral(valeur['tiret'])}",
        f"    const val fleche = {litteral(valeur['fleche'])}",
        "}",
        "",
    ])


def main(argv: list[str]) -> int:
    if len(argv) != 3:
        print(__doc__.strip().splitlines()[-1], file=sys.stderr)
        return 2
    entree, sortie = pathlib.Path(argv[1]), pathlib.Path(argv[2])
    try:
        tables = json.loads(entree.read_text(encoding="utf-8"))["tables"]
        # Les tables communes à plusieurs menus (accès, statut d'une affaire) : leur module.
        sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
        import tables_communes as tc  # noqa: E402 — chargé ici : il importe ce module-ci

        fichiers = {
            (PAQUET, "TablesDuSite.kt"): kotlin(tables, entree.name),
            ("fr.coprofirst.app.commun", "TablesCommunes.kt"): kotlin_commun(tables, entree.name),
            ("fr.coprofirst.app.commun", "AvisDuSite.kt"): kotlin_avis(tables, entree.name),
            (PAQUET_TEXTE, "Separateur.kt"): kotlin_separateurs(tables, entree.name),
            (PAQUET_CHARTE, "NaturesDuFil.kt"): kotlin_natures(tables, entree.name),
            (tc.PAQUET_ACCES, "TablesAcces.kt"): tc.kotlin_acces(tables, entree.name),
            ("fr.coprofirst.app.commun", "StatutsAffaire.kt"): tc.kotlin_statuts_affaire(tables, entree.name),
            ("fr.coprofirst.app.commun", "BandeauSuspendue.kt"): tc.kotlin_bandeau_suspendue(tables, entree.name),
        }
    except TableInattendue as e:
        print(f"❌ {entree.name} : {e}", file=sys.stderr)
        return 1
    for (paquet, nom), texte in fichiers.items():
        fichier = sortie / paquet.replace(".", "/") / nom
        fichier.parent.mkdir(parents=True, exist_ok=True)
        fichier.write_text(texte, encoding="utf-8")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
