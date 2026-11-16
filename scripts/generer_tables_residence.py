#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
"""La capsule du verdict de l'avis sur le règlement (#71), générée au build depuis
`contrat/tables-clients.json` (`badgesDuVerdict`, `BADGES_DU_VERDICT` de
front/src/lib/reglement.ts, publiée depuis v3.105.0), comme l'iOS
(`TablesReglement`, `scripts/generateurs/reglement.py`).

Chaque ton servi a sa classe `badge-*`. Un ton absent ou inconnu prend la classe
`inconnu`, que le site lit comme incertaine. Une classe que l'app ne sait pas teinter
(`teintes` de PastilleTeinte.kt) arrête la génération.

Usage : generer_tables_residence.py <tables-clients.json> <dossier de sortie>
"""

from __future__ import annotations

import json
import pathlib
import re
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from generer_tables import TableInattendue, chaine, valeur  # noqa: E402

PAQUET = "fr.coprofirst.app.fonctions.residence"
# Les classes que `teintes` (commun/PastilleTeinte.kt) sait rendre : une autre se verrait grise.
CLASSES = {"badge-blue", "badge-purple", "badge-orange", "badge-yellow", "badge-green", "badge-red", "badge-gray"}
TON = re.compile(r"^[a-z][a-z_]*$")


def classe(v, ou: str) -> str:
    if v not in CLASSES:
        raise TableInattendue(f"badgesDuVerdict.{ou} : classe que l'app ne teinte pas ({v!r})")
    return v


def kotlin(tables: dict, source: str) -> str:
    badges = valeur(tables, "badgesDuVerdict")
    par_ton = badges.get("parTon") if isinstance(badges, dict) else None
    if not isinstance(par_ton, dict) or not par_ton or not all(isinstance(t, str) and TON.match(t) for t in par_ton):
        raise TableInattendue(f"badgesDuVerdict.parTon : forme inattendue ({par_ton!r})")
    inconnu = classe(badges.get("inconnu"), "inconnu")
    return "\n".join([
        "// GÉNÉRÉ par scripts/generer_tables_residence.py — NE PAS MODIFIER À LA MAIN.",
        f"// Source : {source} (badgesDuVerdict).",
        f"package {PAQUET}",
        "",
        "/** La capsule du verdict de l'avis sur le règlement (`BadgeVerdict` du site). */",
        "object TablesReglement {",
        "    /** La classe de la capsule du verdict, pour chaque ton servi. */",
        "    val badgesDuVerdict: Map<String, String> = mapOf(",
        *[f"        {chaine(t)} to {chaine(classe(c, t))}," for t, c in par_ton.items()],
        "    )",
        "",
        "    /** Un ton absent ou inconnu : le site le lit incertain. */",
        f"    const val BADGE_INCONNU = {chaine(inconnu)}",
        "}",
        "",
    ])


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
    fichier = sortie / PAQUET.replace(".", "/") / "TablesReglement.kt"
    fichier.parent.mkdir(parents=True, exist_ok=True)
    fichier.write_text(code, encoding="utf-8")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
