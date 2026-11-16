#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
"""Tests du générateur des tables de « Mes lots & accès » :
`python3 -I scripts/test_generer_tables_mon_lot.py`."""

import json
import pathlib
import sys
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).parent))
import generer_tables_mon_lot as g  # noqa: E402

RACINE = pathlib.Path(__file__).resolve().parent.parent


def t(v):
    return {"source": "test", "valeur": v}


def tables(**remplace):
    base = {
        "pages": t([{"id": "mon-lot", "onglets": [{"id": "lots", "descriptif": "Vos lots."},
                                                  {"id": "badges", "descriptif": 'Vos "badges".'}]}]),
        "etatsBail": t({"termine": {"libelle": "Terminé", "badge": "badge-gray"}}),
    }
    base.update(remplace)
    return base


class TablesMonLot(unittest.TestCase):
    def test_descriptifs_et_etats_du_bail(self):
        texte = g.kotlin(tables(), "test.json")
        self.assertIn('"lots" to "Vos lots.",', texte)
        self.assertIn('"badges" to "Vos \\"badges\\".",', texte)
        # L'état et la famille d'un accès sont communs (`TablesAcces`, test_generer_tables.py).
        self.assertNotIn("statutsAcces", texte)
        self.assertNotIn("typesAcces", texte)
        self.assertIn('"termine" to LibelleTeinte("Terminé", "badge-gray"),', texte)

    def test_ce_qui_change_de_forme_arrete(self):
        for casse in ({"pages": t([])}, {"etatsBail": t([])}, {"etatsBail": t({"x": {"libelle": "X"}})}):
            with self.assertRaises(g.TableInattendue, msg=str(casse)):
                g.kotlin(tables(**casse), "t")

    def test_le_plancher_se_genere(self):
        publiees = json.loads((RACINE / "contrat" / "tables-clients.json").read_text(encoding="utf-8"))["tables"]
        texte = g.kotlin(publiees, "tables-clients.json")
        for attendu in ('"location" to ', '"telecommandes" to ', '"en_cours_sortie" to '):
            self.assertIn(attendu, texte)


if __name__ == "__main__":
    unittest.main()
