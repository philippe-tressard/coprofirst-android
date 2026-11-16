#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
"""`BalisesDuSite` pour la bibliothèque partagée `:partage-android` (#206, lot 2c).

Le rendu du texte riche du site (`TexteDuSite`, `RenduHTML`) sert le téléphone ET la
montre : sa liste blanche se génère donc dans la bibliothèque, et plus dans `:app`. La
forme du fichier reste celle de `generer_tables.kotlin_balises`, écrite une fois.

Usage : generer_balises.py <tables-clients.json> <dossier de sortie>
"""

from __future__ import annotations

import json
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import generer_tables as gt  # noqa: E402 — chargé par son chemin : le script tourne en `python3 -I`


def main(argv: list[str]) -> int:
    if len(argv) != 3:
        print(__doc__.strip().splitlines()[-1], file=sys.stderr)
        return 2
    entree, sortie = pathlib.Path(argv[1]), pathlib.Path(argv[2])
    try:
        texte = gt.kotlin_balises(json.loads(entree.read_text(encoding="utf-8"))["tables"], entree.name)
    except gt.TableInattendue as e:
        print(f"❌ {entree.name} : {e}", file=sys.stderr)
        return 1
    fichier = sortie / gt.PAQUET_TEXTE.replace(".", "/") / "BalisesDuSite.kt"
    fichier.parent.mkdir(parents=True, exist_ok=True)
    fichier.write_text(texte, encoding="utf-8")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
