#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
"""Les MESURES du site que l'app confronte aux siennes (#2), comme l'iOS (`MesuresDuSite`) :
les jetons de longueur de `jetonsCharte` (`contrat/tables-clients.json`), en dp
(1 rem = 16 dp). `MesuresTest` refuse qu'une mesure de `Mise` en diffère sans le déclarer.

Les COULEURS ne se lisent pas ici : elles viennent résolues de `palettesResolues`
(`generer_charte.py`, #74). Un jeton qui n'est pas une longueur en rem ou px arrête la
génération.

Usage : generer_mesures.py <tables-clients.json> <dossier de sortie>
"""

from __future__ import annotations

import json
import pathlib
import re
import sys

PAQUET = "fr.coprofirst.app.charte"
LONGUEUR = re.compile(r"^([0-9]+(?:\.[0-9]+)?)(rem|px)$")
# Les jetons de longueur que l'app confronte (jeton du site → nom Kotlin).
MESURES = {"--radius": "rayon"}


class LongueurIllisible(ValueError):
    pass


def kotlin(tables: dict, source: str) -> str:
    jetons = tables.get("jetonsCharte", {}).get("valeur", {})
    lignes = [
        "// GÉNÉRÉ par scripts/generer_mesures.py — NE PAS MODIFIER À LA MAIN.",
        f"// Source : {source} (jetonsCharte), jetons de longueur.",
        f"package {PAQUET}",
        "",
        "import androidx.compose.ui.unit.dp",
        "",
        "/** Les mesures du site que l'app confronte aux siennes (`Mise`), en dp (1 rem = 16 dp). */",
        "object MesuresDuSite {",
    ]
    for jeton, nom in MESURES.items():
        valeur = jetons.get(jeton)
        m = LONGUEUR.match(valeur.strip()) if isinstance(valeur, str) else None
        if not m:
            raise LongueurIllisible(f"{jeton} : {valeur!r} n'est pas une longueur en rem ou px")
        nombre = float(m.group(1)) * (16 if m.group(2) == "rem" else 1)
        dp = int(nombre) if nombre == int(nombre) else nombre
        lignes += [f"    /** `{jeton}: {valeur.strip()}` */", f"    val {nom} = {dp}.dp"]
    lignes += ["}", ""]
    return "\n".join(lignes)


def main(argv: list[str]) -> int:
    if len(argv) != 3:
        print(__doc__.strip().splitlines()[-1], file=sys.stderr)
        return 2
    entree, sortie = pathlib.Path(argv[1]), pathlib.Path(argv[2])
    try:
        code = kotlin(json.loads(entree.read_text(encoding="utf-8"))["tables"], entree.name)
    except LongueurIllisible as e:
        print(f"❌ {entree.name} : {e}", file=sys.stderr)
        return 1
    fichier = sortie / PAQUET.replace(".", "/") / "MesuresDuSite.kt"
    fichier.parent.mkdir(parents=True, exist_ok=True)
    fichier.write_text(code, encoding="utf-8")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
