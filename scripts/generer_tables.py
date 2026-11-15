#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
"""Les TABLES du site que l'app lit, générées au build depuis `contrat/tables-clients.json`
(la pièce jointe de la release de coprofirst au plancher) — comme l'iOS
(`scripts/generateurs/onglets.py`) :

- `PagesParDefaut` : les pages de navigation (`pages`, celles qui ont un `href`), dans
  l'ordre par défaut, et le titre du fil (`titreFil`) ;
- `OngletsDuSite` : les onglets de chaque page et leur réserve, les pages réservées à
  l'administration, qui porte chaque droit (`rolesDuDroit`, `statutsDuProfil`).

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
    for p in pages:
        if p.get("href") is None:
            continue  # sans entrée de navigation : ni ordonnée, ni dans la barre
        for cle in ("id", "href", "navLabel", "titre", "icone"):
            if not isinstance(p.get(cle), str):
                raise TableInattendue(f"page {p.get('id')} : {cle} manquant")
        l.append(f"        PageDeNavigation({chaine(p['id'])}, {chaine(p['href'])}, {chaine(p['navLabel'])}, "
                 f"{chaine(p['titre'])}, {chaine(p['icone'])}),")
    l += ["    )", "", "    /** Le titre de la section du fil d'activité (`.titre-fil`). */",
          f"    const val TITRE_DU_FIL: String = {chaine(titre.strip())}", "}", "",
          "/** Les onglets de chaque page du site, dans leur ordre, avec leur réserve ; qui porte chaque droit. */",
          "object OngletsDuSite {", "    val parPage: Map<String, List<OngletDuSite>> = mapOf("]
    reservees = []
    for p in pages:
        if p.get("reserveeAdmin"):
            reservees.append(p["id"])
        onglets = p.get("onglets") or []
        if not onglets:
            continue
        l.append(f"        {chaine(p['id'])} to listOf(")
        for o in onglets:
            r = o.get("reserve")
            if r is not None and r not in RESERVES:
                raise TableInattendue(f"réserve inconnue « {r} » ({p['id']}/{o.get('id')}) — l'app doit l'apprendre")
            reserve = f"ReserveOnglet.{RESERVES[r]}" if r else "null"
            l.append(f"            OngletDuSite({chaine(o['id'])}, {chaine(sans_picto(o.get('label')))}, {reserve}, "
                     f"{'true' if o.get('ouvertOperateur') else 'false'}),")
        l.append("        ),")
    l += ["    )", "",
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


def main(argv: list[str]) -> int:
    if len(argv) != 3:
        print(__doc__.strip().splitlines()[-1], file=sys.stderr)
        return 2
    entree, sortie = pathlib.Path(argv[1]), pathlib.Path(argv[2])
    try:
        texte = kotlin(json.loads(entree.read_text(encoding="utf-8"))["tables"], entree.name)
    except TableInattendue as e:
        print(f"❌ {entree.name} : {e}", file=sys.stderr)
        return 1
    fichier = sortie / PAQUET.replace(".", "/") / "TablesDuSite.kt"
    fichier.parent.mkdir(parents=True, exist_ok=True)
    fichier.write_text(texte, encoding="utf-8")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
