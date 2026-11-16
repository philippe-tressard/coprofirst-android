#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
"""Le bilan des tests d'interface, en Markdown (résumé du job CI, #65) : les rapports JUnit
de `connectedDebugAndroidTest` lus, chaque test en échec nommé avec la première ligne de son
message ; puis les tests les plus lents, avec le délai commun d'attente en regard (#119) —
la mesure qui dira si ce délai est trop court sur l'émulateur de la CI. Aucun rapport lu :
« INCONNU », jamais « aucun échec ».

Le délai commun se lit dans le geste des tests (`DELAI_GESTE_MS`, commun/Gestes.kt) : il
n'est pas recopié ici.

Usage : bilan_tests_interface.py <dossier des rapports>
"""

from __future__ import annotations

import pathlib
import sys
import re
import xml.etree.ElementTree as ET

GESTES = pathlib.Path(__file__).resolve().parent.parent / "tests-interface-communs/fr/coprofirst/app/commun/Gestes.kt"
LES_PLUS_LENTS = 5


def delai_commun_ms(gestes: pathlib.Path = GESTES) -> int | None:
    """`DELAI_GESTE_MS` tel que le geste l'écrit ; illisible : None (dit INCONNU)."""
    try:
        trouve = re.search(r"const val DELAI_GESTE_MS\s*=\s*([\d_]+)L", gestes.read_text(encoding="utf-8"))
    except OSError:
        return None
    return int(trouve.group(1).replace("_", "")) if trouve else None


def bilan(dossier: pathlib.Path, delai_ms: int | None = None) -> str:
    rapports = sorted(dossier.rglob("*.xml")) if dossier.is_dir() else []
    echecs, durees, lus = [], {}, 0
    for rapport in rapports:
        try:
            racine = ET.parse(rapport).getroot()
        except ET.ParseError:
            continue
        lus += 1
        for cas in racine.iter("testcase"):
            nom = f"{cas.get('classname')}.{cas.get('name')}"
            try:
                durees[nom] = max(durees.get(nom, 0.0), float(cas.get("time") or 0))
            except ValueError:
                pass
            defaut = cas.find("failure") if cas.find("failure") is not None else cas.find("error")
            if defaut is not None:
                message = (defaut.get("message") or defaut.text or "").strip().splitlines()
                echecs.append(f"- `{cas.get('classname')}.{cas.get('name')}` — {message[0][:600] if message else '(sans message)'}")
    lignes = ["### Tests d'interface en échec"]
    if not lus:
        lignes.append("❓ INCONNU : aucun rapport lu (le job a échoué avant les tests ?)")
        return "\n".join(lignes) + "\n"
    if not echecs:
        lignes.append("Aucun test en échec dans les rapports.")
    else:
        lignes += sorted(set(echecs))
    delai = f"{delai_ms / 1000:g} s" if delai_ms else "❓ INCONNU"
    lignes += ["", f"### Les {LES_PLUS_LENTS} tests les plus lents (délai commun d'attente : {delai})", "",
               "| Test | Durée |", "|---|---|"]
    for nom, duree in sorted(durees.items(), key=lambda d: -d[1])[:LES_PLUS_LENTS]:
        lignes.append(f"| `{nom}` | {duree:.1f} s |")
    return "\n".join(lignes) + "\n"


if __name__ == "__main__":
    if len(sys.argv) != 2:
        print(__doc__.strip().splitlines()[-1], file=sys.stderr)
        sys.exit(2)
    sys.stdout.write(bilan(pathlib.Path(sys.argv[1]), delai_commun_ms()))
