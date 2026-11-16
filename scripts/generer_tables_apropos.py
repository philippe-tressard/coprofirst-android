#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
"""Les textes du pied de page du site, pour « À propos » (#27), générés au build depuis
`contrat/tables-clients.json` — comme l'iOS (`TextesDuPied`, `scripts/generateurs/a_propos.py`) :

- le logiciel, sa licence et son dépôt (`plateforme`, `plateforme.ts`) ;
- le libellé et l'infobulle de chaque lien du pied (`elementsPied`, `piedDePage.ts`) ;
- les clés de GET /config qui règlent la mention (`clesPied`, `CLES_PIED`).

Une table qui change de forme : la génération s'arrête.

Usage : generer_tables_apropos.py <tables-clients.json> <dossier de sortie>
"""

from __future__ import annotations

import json
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from generer_tables import TableInattendue, chaine  # noqa: E402
from generer_tables_mon_lot import texte, valeur  # noqa: E402

PAQUET = "fr.coprofirst.app.fonctions.apropos"
# Clé de `plateforme` → nom Kotlin (ceux de l'iOS : `nom` et `site` y portent « Plateforme »).
PLATEFORME = {"nom": "nomPlateforme", "site": "sitePlateforme", "depotSource": "depotSource",
              "licenceSpdx": "licenceSpdx", "licenceNom": "licenceNom", "licenceUrl": "licenceUrl"}
LIENS = ("site", "source", "mentions", "confidentialite")
CLES = ("anneeDebut", "texte")


def kotlin(tables: dict, source: str) -> str:
    plateforme = valeur(tables, "plateforme")
    elements = {e.get("code"): e for e in valeur(tables, "elementsPied")}
    cles = valeur(tables, "clesPied")
    manque = [c for c in LIENS if c not in elements]
    if manque:
        raise TableInattendue(f"elementsPied : {', '.join(manque)} absent")
    l = [
        "// GÉNÉRÉ par scripts/generer_tables_apropos.py — NE PAS MODIFIER À LA MAIN.",
        f"// Source : {source} (plateforme, elementsPied, clesPied).",
        f"package {PAQUET}",
        "",
        "/** Un lien du pied de page : son libellé, et l'infobulle du site s'il en a une. */",
        "data class LienDuPied(val libelle: String, val aide: String)",
        "",
        "/** Ce que le pied de page du site dit sur chaque page : le logiciel, sa licence, ses liens. */",
        "object TextesDuPied {",
    ]
    l += [f"    const val {k} = {chaine(texte(plateforme, c, 'plateforme'))}" for c, k in PLATEFORME.items()]
    for code in LIENS:
        e = elements[code]
        aide = (e.get("lien") or {}).get("infobulle", "")
        l.append(f"    val {code} = LienDuPied({chaine(texte(e, 'libelle', 'elementsPied'))}, {chaine(aide)})")
    l.append("")
    l.append("    /** Les clés de GET /config qui règlent la mention (`CLES_PIED`). */")
    l += [f"    const val cle{c[0].upper()}{c[1:]} = {chaine(texte(cles, c, 'clesPied'))}" for c in CLES]
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
    fichier = sortie / PAQUET.replace(".", "/") / "TextesDuPied.kt"
    fichier.parent.mkdir(parents=True, exist_ok=True)
    fichier.write_text(code, encoding="utf-8")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
