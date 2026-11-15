#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
"""Tests du générateur des tables du Profil : `python3 -I scripts/test_generer_tables_profil.py`."""

import json
import pathlib
import sys
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).parent))
import generer_tables_profil as g  # noqa: E402
from generer_tables import TableInattendue  # noqa: E402

RACINE = pathlib.Path(__file__).resolve().parent.parent


def t(v):
    return {"source": "test", "valeur": v}


TABLES = {
    "attributsStatut": t({"locataire": {"libelle": "Locataire", "badge": "badge-purple"}}),
    "libellesHerites": t({"bailleur": "Copropriétaire bailleur"}),
    "attributsRole": t({"conseil_syndical": {"libelle": "Conseil syndical", "badge": "badge-blue"}}),
    "rolesMisEnAvant": t(["conseil_syndical"]),
    "statutsDemandables": t(["locataire", "syndic"]),
}


class TablesProfil(unittest.TestCase):
    def test_libelles_et_listes(self):
        texte = g.kotlin(TABLES, "test.json")
        self.assertIn('"locataire" to "Locataire",', texte)
        self.assertIn('"bailleur" to "Copropriétaire bailleur",', texte)
        self.assertIn('"conseil_syndical" to "Conseil syndical",', texte)
        self.assertIn('val statutsDemandables: List<String> = listOf("locataire", "syndic")', texte)
        self.assertTrue(texte.endswith("}\n"))

    def test_ce_qui_change_de_forme_arrete(self):
        for table, faux in [("attributsStatut", t({"locataire": {"badge": "x"}})), ("statutsDemandables", t([])),
                            ("rolesMisEnAvant", t("admin"))]:
            with self.subTest(table=table), self.assertRaises(TableInattendue):
                g.kotlin({**TABLES, table: faux}, "test.json")
        with self.assertRaises(TableInattendue):
            g.kotlin({k: v for k, v in TABLES.items() if k != "attributsRole"}, "test.json")

    def test_le_plancher_se_genere(self):
        tables = json.loads((RACINE / "contrat" / "tables-clients.json").read_text(encoding="utf-8"))["tables"]
        texte = g.kotlin(tables, "tables-clients.json")
        self.assertIn('"copropriétaire_résident" to "Copropriétaire résident",', texte)
        self.assertIn("val rolesMisEnAvant", texte)


if __name__ == "__main__":
    unittest.main(verbosity=1)
