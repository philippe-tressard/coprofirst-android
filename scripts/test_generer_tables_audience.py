#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
"""Tests du générateur des bornes de la mesure d'audience :
`python3 -I scripts/test_generer_tables_audience.py`."""

import copy
import json
import pathlib
import sys
import tempfile
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).parent))
import generer_tables_audience as g  # noqa: E402
from generer_tables import TableInattendue  # noqa: E402

RACINE = pathlib.Path(__file__).resolve().parent.parent

SCHEMA = {
    "paths": {g.ROUTE: {"post": {"requestBody": {"content": {"application/json": {
        "schema": {"$ref": f"#/components/schemas/{g.LOT}"}}}}}}},
    "components": {"schemas": {
        g.LOT: {"properties": {"events": {"items": {"$ref": f"#/components/schemas/{g.EVENEMENT}"}, "maxItems": 7}}},
        g.EVENEMENT: {"properties": {"page": {"maxLength": 50}, "action": {"maxLength": 9}}},
    }},
}


def schema(modifier=None):
    s = copy.deepcopy(SCHEMA)
    if modifier:
        modifier(s)
    return s


class LimitesAudience(unittest.TestCase):
    def test_les_trois_bornes(self):
        texte = g.kotlin(schema(), "t")
        for attendu in ("const val EVENEMENTS_PAR_LOT: Int = 7", "const val PAGE: Int = 50",
                        "const val ACTION: Int = 9"):
            self.assertIn(attendu, texte)

    def test_ce_qui_change_de_forme_arrete(self):
        def corps(s):
            s["paths"][g.ROUTE]["post"]["requestBody"]["content"]["application/json"]["schema"]["$ref"] = "#/x"

        def items(s):
            s["components"]["schemas"][g.LOT]["properties"]["events"]["items"] = {}

        def booleen(s):
            s["components"]["schemas"][g.LOT]["properties"]["events"]["maxItems"] = True

        def zero(s):
            s["components"]["schemas"][g.EVENEMENT]["properties"]["page"]["maxLength"] = 0

        def absente(s):
            del s["paths"][g.ROUTE]

        for casse in (corps, items, booleen, zero, absente):
            with self.assertRaises(TableInattendue, msg=casse.__name__):
                g.kotlin(schema(casse), "t")

    def test_lit_openapi_a_cote_des_tables(self):
        with tempfile.TemporaryDirectory() as d:
            dossier = pathlib.Path(d)
            (dossier / "openapi.json").write_text(json.dumps(SCHEMA), encoding="utf-8")
            self.assertEqual(0, g.main(["x", str(dossier / "tables-clients.json"), str(dossier / "sortie")]))
            sortie = dossier / "sortie" / g.PAQUET.replace(".", "/") / "LimitesAudience.kt"
            self.assertIn("EVENEMENTS_PAR_LOT: Int = 7", sortie.read_text(encoding="utf-8"))

    def test_le_plancher_se_genere(self):
        publie = json.loads((RACINE / "contrat" / "openapi.json").read_text(encoding="utf-8"))
        texte = g.kotlin(publie, "openapi.json")
        self.assertIn("const val EVENEMENTS_PAR_LOT: Int = 20", texte)
        self.assertIn("const val PAGE: Int = 200", texte)


if __name__ == "__main__":
    unittest.main()
