#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
"""Les tables de « Mes lots & accès » (#18), générées au build depuis
`contrat/tables-clients.json` — comme l'iOS (`scripts/generateurs/acces_prestataires.py`).

Le contrat d'API ne sert que des CODES (`StatutAcces`, `StatutBail`) : les libellés et
les teintes vivent dans le front du site, qui les publie dans `tables-clients.json` :

- le descriptif de chaque onglet de la page `mon-lot` (`pages`) — les onglets
  eux-mêmes sont dans `OngletsDuSite` (scripts/generer_tables.py) ;
- `statutsAcces` et `typesAcces` (`types-acces.ts`) ;
- l'état d'un bail (`etatsBail`, `ETATS_BAIL` de `bail.ts`).

Une table qui change de forme : la génération s'arrête.

Usage : generer_tables_mon_lot.py <tables-clients.json> <dossier de sortie>
"""

from __future__ import annotations

import json
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from generer_tables import TableInattendue, chaine  # noqa: E402

PAQUET = "fr.coprofirst.app.fonctions.monlot"


def valeur(tables: dict, nom: str):
    v = tables.get(nom, {}).get("valeur")
    if v is None:
        raise TableInattendue(f"{nom} absente")
    return v


def texte(entree: dict, cle: str, table: str) -> str:
    v = entree.get(cle)
    if not isinstance(v, str):
        raise TableInattendue(f"{table} : {cle} manquant ({entree})")
    return v


def teintes(nom: str, entrees: list[tuple[str, str, str]], doc: str) -> list[str]:
    return [f"    /** {doc} */", f"    val {nom}: Map<String, LibelleTeinte> = mapOf("] + [
        f"        {chaine(c)} to LibelleTeinte({chaine(l)}, {chaine(b)})," for c, l, b in entrees] + ["    )", ""]


def kotlin(tables: dict, source: str) -> str:
    page = next((p for p in valeur(tables, "pages") if p.get("id") == "mon-lot"), None)
    if not page or not page.get("onglets"):
        raise TableInattendue("la page mon-lot et ses onglets sont absents")
    statuts = [(texte(s, "val", "statutsAcces"), texte(s, "label", "statutsAcces"), texte(s, "badge", "statutsAcces"))
               for s in valeur(tables, "statutsAcces")]
    types = [(texte(t, "val", "typesAcces"), texte(t, "label", "typesAcces")) for t in valeur(tables, "typesAcces")]
    bail = valeur(tables, "etatsBail")
    if not isinstance(bail, dict) or not bail:
        raise TableInattendue("etatsBail n'est pas une table")
    etats = [(c, texte(v, "libelle", "etatsBail"), texte(v, "badge", "etatsBail")) for c, v in bail.items()]
    l = [
        "// GÉNÉRÉ par scripts/generer_tables_mon_lot.py — NE PAS MODIFIER À LA MAIN.",
        f"// Source : {source} (pages, statutsAcces, typesAcces, etatsBail).",
        f"package {PAQUET}",
        "",
        "import fr.coprofirst.app.commun.LibelleTeinte",
        "",
        "/** Ce que le site écrit de « Mes lots & accès » et que le contrat ne sert pas. */",
        "object TablesMonLot {",
        "    /** Le descriptif de chaque onglet de la page `mon-lot` (texte du site, parfois HTML). */",
        "    val descriptifs: Map<String, String> = mapOf(",
    ]
    l += [f"        {chaine(texte(o, 'id', 'pages'))} to {chaine(o.get('descriptif', ''))}," for o in page["onglets"]]
    l += ["    )", ""]
    l += teintes("statutsAcces", statuts, "L'état d'un badge ou d'une télécommande (`STATUTS_ACCES`).")
    l += ["    /** Les deux familles d'accès, dans l'ordre du site (`TYPES_ACCES`) : code → libellé. */",
          "    val typesAcces: List<Pair<String, String>> = listOf("]
    l += [f"        {chaine(c)} to {chaine(lib)}," for c, lib in types]
    l += ["    )", ""]
    l += teintes("statutsBail", etats, "L'état d'un bail (`ETATS_BAIL`).")
    l += ["}", ""]
    return "\n".join(l)


def main(argv: list[str]) -> int:
    if len(argv) != 3:
        print(__doc__.strip().splitlines()[-1], file=sys.stderr)
        return 2
    entree, sortie = pathlib.Path(argv[1]), pathlib.Path(argv[2])
    try:
        code = kotlin(json.loads(entree.read_text(encoding="utf-8"))["tables"], entree.name)
    except TableInattendue as e:
        print(f"❌ {entree.name} : {e}", file=sys.stderr)
        return 1
    fichier = sortie / PAQUET.replace(".", "/") / "TablesMonLot.kt"
    fichier.parent.mkdir(parents=True, exist_ok=True)
    fichier.write_text(code, encoding="utf-8")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
