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
  comme l'iOS (`scripts/generateurs/charte.py`, `balises`) ;
- `TablesCommunes` (paquet `commun`) : le libellé et la pastille de chaque statut et de
  chaque rôle (`attributsStatut`, `attributsRole`, `libellesHerites`), les rôles mis en
  avant (`rolesMisEnAvant`), les statuts qu'un compte peut demander
  (`statutsDemandables`), les options du thème de l'appareil (`optionsTheme`,
  `themeParDefaut`, `cookieTheme`) ;
- `FichiersDuSite` (paquet `commun.pieces`) : ce qu'est une photo parmi les pièces
  jointes, le préfixe retiré du nom montré, la famille de chaque extension — comme l'iOS
  (`scripts/generateurs/fichiers.py`), depuis les constantes `FICHIERS_*` ci-dessous.

Une table qui change de forme, une réserve inconnue : la génération s'arrête — l'app
doit l'apprendre, pas la deviner.

Usage : generer_tables.py <tables-clients.json> <dossier de sortie>
"""

from __future__ import annotations

import json
import pathlib
import re
import sys

PAQUET = "fr.coprofirst.app.navigation"
PAQUET_TEXTE = "fr.coprofirst.app.commun.texte"
PAQUET_PIECES = "fr.coprofirst.app.commun.pieces"
# Les schémas d'un lien touchable : SCHEMAS_URL de coprofirst (api/app/utils/html_sur.py),
# non publiés dans tables-clients.json — à générer quand coprofirst#2757 les publiera.
SCHEMAS_DE_LIEN = ("http", "https", "mailto")
# Les pièces jointes comme le site les range et les nomme : `EXTENSIONS_IMAGE` (dépliée :
# `jpe?g` → jpeg, jpg ; `tiff?` → tif, tiff), `PREFIXE_UUID` et les familles de
# `typeFichier` de coprofirst (front/src/lib/fichiers.ts), NON publiés dans
# tables-clients.json — l'iOS les lit dans la source du front (`generateurs/fichiers.py`).
# Mêmes valeurs que sa table générée (v3.101.0) ; à générer quand coprofirst les publiera
# (coprofirst#2764).
FICHIERS_EXTENSIONS_IMAGE = ("avif", "bmp", "gif", "heic", "heif", "jpeg", "jpg", "png", "tif", "tiff", "webp")
FICHIERS_LONGUEUR_PREFIXE = 32
FICHIERS_FAMILLES = (("JPEG", "JPG"), ("XLSX", "EXCEL"), ("XLS", "EXCEL"), ("DOCX", "WORD"), ("DOC", "WORD"),
                     ("PPTX", "POWERPOINT"), ("PPT", "POWERPOINT"), ("TXT", "TEXTE"))
EXTENSION = re.compile(r"^[a-z0-9]+$")
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
        l.append(f"        {page_kotlin(p, p['href'])},")
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
          f"    val rolesAdminSeul: Set<String> = {ensemble(liste(tables, 'rolesDuDroit', 'adminSeul'))}",
          f"    val rolesSansAdminSeul: Set<String> = {ensemble(liste(tables, 'rolesDuDroit', 'sansAdminSeul'))}",
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
        f"// Source : {source} (balisesAdmises, attributsAdmis) ; schémas : SCHEMAS_DE_LIEN du générateur.",
        f"package {PAQUET_TEXTE}",
        "",
        "/** La liste blanche du texte riche du site : ce que l'app rend, et rien d'autre. */",
        "object BalisesDuSite {",
        f"    val balises: Set<String> = {ensemble(balises)}",
        f"    val attributs: Set<String> = {ensemble(attributs)}",
        "",
        "    /** Les schémas d'un lien touchable (`SCHEMAS_URL`, appliqué par l'API). */",
        f"    val schemasDeLien: Set<String> = {ensemble(list(SCHEMAS_DE_LIEN))}",
        "}",
        "",
    ])


def kotlin_fichiers(extensions=FICHIERS_EXTENSIONS_IMAGE, prefixe=FICHIERS_LONGUEUR_PREFIXE,
                    familles=FICHIERS_FAMILLES) -> str:
    """`FichiersDuSite` : photo ou document, nom montré, type montré — la règle du site."""
    if (not isinstance(extensions, (list, tuple)) or not extensions or len(set(extensions)) != len(extensions)
            or not all(isinstance(e, str) and EXTENSION.match(e) for e in extensions)):
        raise TableInattendue("EXTENSIONS_IMAGE n'est pas une liste d'extensions en minuscules, sans doublon")
    if not isinstance(prefixe, int) or isinstance(prefixe, bool) or prefixe < 1:
        raise TableInattendue("PREFIXE_UUID n'est pas une longueur")
    cles = [c for c, _ in familles] if isinstance(familles, (list, tuple)) else []
    if (not cles or len(set(cles)) != len(cles)
            or not all(isinstance(c, str) and isinstance(v, str) and EXTENSION.match(c.lower()) and c == c.upper()
                       and v.strip() and v == v.upper() for c, v in familles)):
        raise TableInattendue("les familles de typeFichier ne sont pas des extensions en capitales, sans doublon")
    return "\n".join([
        "// GÉNÉRÉ par scripts/generer_tables.py — NE PAS MODIFIER À LA MAIN.",
        "// Source : coprofirst front/src/lib/fichiers.ts (EXTENSIONS_IMAGE, PREFIXE_UUID, typeFichier),",
        "// non publiés par tables-clients.json : FICHIERS_* du générateur, aux valeurs de l'iOS.",
        f"package {PAQUET_PIECES}",
        "",
        "/** Les pièces jointes comme le site les range et les nomme. */",
        "object FichiersDuSite {",
        "    /** Les extensions d'une image affichable en vignette (`EXTENSIONS_IMAGE`). */",
        f"    val extensionsImage: Set<String> = {ensemble(list(extensions))}",
        "",
        "    /**",
        "     * Le préfixe que le serveur ajoute au nom téléversé : tant de chiffres",
        "     * hexadécimaux, puis « _ » (`PREFIXE_UUID`) — retiré du nom montré.",
        "     */",
        f"    const val LONGUEUR_PREFIXE: Int = {prefixe}",
        "",
        "    /** L'extension en capitales, ramenée à sa famille (`typeFichier`). */",
        "    val familles: Map<String, String> = mapOf(",
        *[f"        {chaine(c)} to {chaine(v)}," for c, v in familles],
        "    )",
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


def page_kotlin(p: dict, route: str) -> str:
    return (f"PageDeNavigation({chaine(p['id'])}, {chaine(route)}, {chaine(p['navLabel'])}, "
            f"{chaine(p['titre'])}, {chaine(p['icone'])})")


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


def main(argv: list[str]) -> int:
    if len(argv) != 3:
        print(__doc__.strip().splitlines()[-1], file=sys.stderr)
        return 2
    entree, sortie = pathlib.Path(argv[1]), pathlib.Path(argv[2])
    try:
        tables = json.loads(entree.read_text(encoding="utf-8"))["tables"]
        fichiers = {
            (PAQUET, "TablesDuSite.kt"): kotlin(tables, entree.name),
            ("fr.coprofirst.app.commun", "TablesCommunes.kt"): kotlin_commun(tables, entree.name),
            ("fr.coprofirst.app.commun", "AvisDuSite.kt"): kotlin_avis(tables, entree.name),
            (PAQUET_TEXTE, "BalisesDuSite.kt"): kotlin_balises(tables, entree.name),
            (PAQUET_PIECES, "FichiersDuSite.kt"): kotlin_fichiers(),
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
