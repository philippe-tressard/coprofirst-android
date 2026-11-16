#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
"""Tests du bilan des tests d'interface : `python3 -I scripts/test_bilan_tests_interface.py`."""

import pathlib
import sys
import tempfile
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).parent))
import bilan_tests_interface as b  # noqa: E402

RAPPORT = """<testsuite>
  <testcase classname="a.FilTest" name="vite" time="1.5"/>
  <testcase classname="a.FilTest" name="lent" time="12.25"><failure message="Condition still not satisfied after 10000 ms">x</failure></testcase>
  <testcase classname="a.ProfilTest" name="moyen" time="4"/>
</testsuite>"""


class Bilan(unittest.TestCase):
    def test_echecs_puis_les_plus_lents_et_le_delai(self):
        with tempfile.TemporaryDirectory() as d:
            (pathlib.Path(d) / "r.xml").write_text(RAPPORT, encoding="utf-8")
            texte = b.bilan(pathlib.Path(d), 10_000)
        self.assertIn("- `a.FilTest.lent` — Condition still not satisfied after 10000 ms", texte)
        self.assertIn("délai commun d'attente : 10 s", texte)
        lignes = [l for l in texte.splitlines() if l.startswith("| `")]
        self.assertEqual(["| `a.FilTest.lent` | 12.2 s |", "| `a.ProfilTest.moyen` | 4.0 s |", "| `a.FilTest.vite` | 1.5 s |"], lignes)

    def test_sans_rapport_inconnu(self):
        with tempfile.TemporaryDirectory() as d:
            self.assertIn("INCONNU", b.bilan(pathlib.Path(d), 10_000))

    def test_le_delai_se_lit_dans_le_geste(self):
        self.assertIsNotNone(b.delai_commun_ms())


if __name__ == "__main__":
    unittest.main()
