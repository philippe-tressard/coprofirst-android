#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
"""Les tables des « Délégations aidant » (#25), générées au build depuis
`contrat/tables-clients.json` — comme l'iOS (`scripts/generateurs/delegations_cs.py`).

Le contrat d'API ne sert que le CODE d'un état (`DelegationRead.statut`) : le libellé et
la teinte vivent dans le front du site, qui les publie dans `tables-clients.json` :

- le descriptif de la page `delegations` (`pages`) : ce qu'une délégation permet ;
- l'état d'une délégation (`statutsDelegation`, `STATUTS_DELEGATION` de `delegations.ts`).

Une table qui change de forme : la génération s'arrête.

Usage : generer_tables_delegations.py <tables-clients.json> <dossier de sortie>
"""

from __future__ import annotations

import json
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from generer_tables import TableInattendue, chaine, teintes, texte, valeur  # noqa: E402

PAQUET = "fr.coprofirst.app.fonctions.delegations"


def kotlin(tables: dict, source: str) -> str:
    page = next((p for p in valeur(tables, "pages") if p.get("id") == "delegations"), None)
    if not page:
        raise TableInattendue("la page delegations est absente")
    descriptif = texte(page, "descriptif", "pages")
    statuts = valeur(tables, "statutsDelegation")
    if not isinstance(statuts, dict) or not statuts:
        raise TableInattendue("statutsDelegation n'est pas une table")
    etats = [(c, texte(v, "libelle", "statutsDelegation"), texte(v, "badge", "statutsDelegation"))
             for c, v in statuts.items()]
    l = [
        "// GÉNÉRÉ par scripts/generer_tables_delegations.py — NE PAS MODIFIER À LA MAIN.",
        f"// Source : {source} (pages, statutsDelegation).",
        f"package {PAQUET}",
        "",
        "import fr.coprofirst.app.commun.LibelleTeinte",
        "",
        "/** Ce que le site écrit des « Délégations aidant » et que le contrat ne sert pas. */",
        "object TablesDelegations {",
        "    /** Le descriptif de la page (`pages-roles.ts`) : ce qu'une délégation permet. */",
        f"    const val DESCRIPTIF: String = {chaine(descriptif)}",
        "",
    ]
    l += teintes("statuts", etats, "L'état d'une délégation (`STATUTS_DELEGATION`).")
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
    fichier = sortie / PAQUET.replace(".", "/") / "TablesDelegations.kt"
    fichier.parent.mkdir(parents=True, exist_ok=True)
    fichier.write_text(code, encoding="utf-8")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
