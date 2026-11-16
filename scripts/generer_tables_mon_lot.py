#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
"""Les tables de « Mes lots & accès » (#18), générées au build depuis
`contrat/tables-clients.json` — comme l'iOS (`scripts/generateurs/acces_prestataires.py`).

Le contrat d'API ne sert que des CODES (`StatutAcces`, `StatutBail`) : les libellés et
les teintes vivent dans le front du site, qui les publie dans `tables-clients.json` :

- le descriptif de chaque onglet de la page `mon-lot` (`pages`) — les onglets
  eux-mêmes sont dans `OngletsDuSite` (scripts/generer_tables.py) ;
- l'état d'un bail (`etatsBail`, `ETATS_BAIL` de `bail.ts`) ;
- le type et le statut d'un objet remis au locataire (`typesObjetBail`,
  `statutsObjetBail`, `bail.ts`) ;
- la gravité d'un constat, du plus grave au moins grave (`gravitesConstat`,
  `constats.ts`) — l'ordre de la table est celui de l'écran (#74).

L'état et la famille d'un accès (`statutsAcces`, `typesAcces`) sont communs à deux
menus : `TablesAcces`, générée par scripts/generer_tables.py (#79).

Une table qui change de forme : la génération s'arrête.

Usage : generer_tables_mon_lot.py <tables-clients.json> <dossier de sortie>
"""

from __future__ import annotations

import json
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from generer_tables import TableInattendue, chaine, teintes, texte, valeur  # noqa: E402

PAQUET = "fr.coprofirst.app.fonctions.monlot"


def table_teintes(tables: dict, nom: str) -> list[tuple[str, str, str]]:
    """Une table `code → {libelle, badge}` du site, dans son ordre."""
    t = valeur(tables, nom)
    if not isinstance(t, dict) or not t:
        raise TableInattendue(f"{nom} n'est pas une table")
    return [(c, texte(v, "libelle", nom), texte(v, "badge", nom)) for c, v in t.items()]


def kotlin(tables: dict, source: str) -> str:
    page = next((p for p in valeur(tables, "pages") if p.get("id") == "mon-lot"), None)
    if not page or not page.get("onglets"):
        raise TableInattendue("la page mon-lot et ses onglets sont absents")
    etats, statuts_objet, gravites = (table_teintes(tables, n) for n in ("etatsBail", "statutsObjetBail", "gravitesConstat"))
    objets = valeur(tables, "typesObjetBail")
    if not isinstance(objets, list) or not objets:
        raise TableInattendue("typesObjetBail n'est pas une liste")
    l = [
        "// GÉNÉRÉ par scripts/generer_tables_mon_lot.py — NE PAS MODIFIER À LA MAIN.",
        f"// Source : {source} (pages, etatsBail, typesObjetBail, statutsObjetBail, gravitesConstat).",
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
    l += teintes("statutsBail", etats, "L'état d'un bail (`ETATS_BAIL`).")
    l += ["    /** Le type d'un objet remis au locataire (`TYPES_OBJET_BAIL`) : code → libellé. */",
          "    val typesObjet: Map<String, String> = mapOf("]
    l += [f"        {chaine(texte(o, 'val', 'typesObjetBail'))} to {chaine(texte(o, 'label', 'typesObjetBail'))}," for o in objets]
    l += ["    )", ""]
    l += teintes("statutsObjet", statuts_objet, "Le statut d'un objet remis (`STATUTS_OBJET_BAIL`).")
    l += teintes("gravites", gravites, "La gravité d'un constat, du plus grave au moins grave (`GRAVITES_CONSTAT`).")
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
