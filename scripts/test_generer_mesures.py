#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
"""Tests du générateur des mesures du site : `python3 -I scripts/test_generer_mesures.py`."""

import json
import pathlib
import sys
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).parent))
import generer_mesures as g  # noqa: E402

RACINE = pathlib.Path(__file__).resolve().parent.parent


def tables(jetons):
    return {"jetonsCharte": {"source": "test", "valeur": jetons}}


class MesuresDuSite(unittest.TestCase):
    def test_le_rem_devient_des_dp(self):
        texte = g.kotlin(tables({"--radius": "0.5rem"}), "t")
        self.assertIn("val rayon = 8.dp", texte)
        self.assertIn("`--radius: 0.5rem`", texte)

    def test_le_px_reste_des_dp(self):
        self.assertIn("val rayon = 6.dp", g.kotlin(tables({"--radius": "6px"}), "t"))

    def test_ce_qui_n_est_pas_une_longueur_arrete(self):
        for casse in ({"--radius": "calc(1rem / 2)"}, {"--radius": "large"}, {}):
            with self.assertRaises(g.LongueurIllisible, msg=str(casse)):
                g.kotlin(tables(casse), "t")

    def test_le_plancher_se_genere(self):
        publiees = json.loads((RACINE / "contrat" / "tables-clients.json").read_text(encoding="utf-8"))["tables"]
        self.assertIn("val rayon = 8.dp", g.kotlin(publiees, "tables-clients.json"))


if __name__ == "__main__":
    unittest.main()
