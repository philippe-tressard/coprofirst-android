#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
"""Le bilan des tests d'interface en échec, en Markdown (résumé du job CI, #65) : les
rapports JUnit de `connectedDebugAndroidTest` lus, chaque test en échec nommé avec la
première ligne de son message. Aucun rapport lu : « INCONNU », jamais « aucun échec ».

Usage : bilan_tests_interface.py <dossier des rapports>
"""

from __future__ import annotations

import pathlib
import sys
import xml.etree.ElementTree as ET


def bilan(dossier: pathlib.Path) -> str:
    rapports = sorted(dossier.rglob("*.xml")) if dossier.is_dir() else []
    echecs, lus = [], 0
    for rapport in rapports:
        try:
            racine = ET.parse(rapport).getroot()
        except ET.ParseError:
            continue
        lus += 1
        for cas in racine.iter("testcase"):
            defaut = cas.find("failure") if cas.find("failure") is not None else cas.find("error")
            if defaut is not None:
                message = (defaut.get("message") or defaut.text or "").strip().splitlines()
                echecs.append(f"- `{cas.get('classname')}.{cas.get('name')}` — {message[0][:200] if message else '(sans message)'}")
    lignes = ["### Tests d'interface en échec"]
    if not lus:
        lignes.append("❓ INCONNU : aucun rapport lu (le job a échoué avant les tests ?)")
    elif not echecs:
        lignes.append("Aucun test en échec dans les rapports : l'échec est ailleurs (émulateur, installation).")
    else:
        lignes += sorted(set(echecs))
    return "\n".join(lignes) + "\n"


if __name__ == "__main__":
    if len(sys.argv) != 2:
        print(__doc__.strip().splitlines()[-1], file=sys.stderr)
        sys.exit(2)
    sys.stdout.write(bilan(pathlib.Path(sys.argv[1])))
