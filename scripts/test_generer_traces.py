#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
"""Tests du générateur des tracés des icônes du site (#183) :
`python3 -I scripts/test_generer_traces.py`."""

import json
import pathlib
import sys
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).parent))
import generer_traces as g  # noqa: E402
from generer_tables import TableInattendue  # noqa: E402

RACINE = pathlib.Path(__file__).resolve().parent.parent
TRAIT = {"attributsTrace": {"valeur": {"viewBox": "0 0 24 24", "fill": "none", "stroke-width": "1.5",
                                        "stroke-linecap": "round", "stroke-linejoin": "round"}}}


class Elements(unittest.TestCase):
    def test_chaque_element_devient_un_chemin(self):
        cas = {
            '<path d="M3 21h4"/>': "M3 21h4",
            '<line x1="16" x2="16" y1="2" y2="6"/>': "M16,2L16,6",
            '<polyline points="22 12 18 12 15 21"/>': "M22,12L18,12L15,21",
            '<circle cx="12" cy="12" r="10"/>': "M2,12a10,10 0 1,0 20,0a10,10 0 1,0 -20,0Z",
            '<ellipse cx="12" cy="5" rx="9" ry="3"/>': "M3,5a9,3 0 1,0 18,0a9,3 0 1,0 -18,0Z",
            '<rect x="3" y="4" width="18" height="18"/>': "M3,4h18v18h-18Z",
            '<rect x="3" y="4" width="18" height="18" rx="2"/>':
                "M5,4h14a2,2 0 0 1 2,2v14a2,2 0 0 1 -2,2h-14a2,2 0 0 1 -2,-2v-14a2,2 0 0 1 2,-2Z",
        }
        for svg, attendu in cas.items():
            self.assertEqual([(attendu, False, True)], g.traits("x", svg), svg)

    def test_plein_et_sans_trait(self):
        self.assertEqual([("M1 1", True, True)], g.traits("x", '<path d="M1 1" fill="currentColor"/>'))
        self.assertEqual([("M1 1", True, False)], g.traits("x", '<path d="M1 1" fill="#25D366" stroke="none"/>'))

    def test_ce_qui_ne_se_rend_pas_arrete(self):
        for svg in ('<g><path d="M1 1"/></g>', '<polygon points="1 1 2 2 3 1"/>', '<path d="M1 1" transform="rotate(4)"/>',
                    '<circle cx="1" cy="1"/>', '<path d="M1 1" stroke="none"/>', "", '<line x1="a" y1="0" x2="1" y2="1"/>'):
            with self.assertRaises(TableInattendue, msg=svg):
                g.traits("x", svg)


class Catalogue(unittest.TestCase):
    def test_le_catalogue_publie_se_genere(self):
        icones = json.loads((RACINE / "contrat" / "icones-svg.json").read_text(encoding="utf-8"))
        tables = json.loads((RACINE / "contrat" / "tables-clients.json").read_text(encoding="utf-8"))["tables"]
        texte = g.kotlin(icones, tables, "icones-svg.json")
        self.assertIn("const val EPAISSEUR = 1.5f", texte)
        self.assertIn('"bell" to listOf(', texte)
        self.assertEqual(len(icones), texte.count('" to listOf('))
        # Chaque nature du fil a son tracé : la gouttière ne tombe jamais sur le repli.
        for nature in tables["naturesDuFil"]["valeur"].values():
            self.assertIn(f'"{nature["icone"]}" to listOf(', texte, nature["icone"])

    def test_un_trait_inattendu_arrete(self):
        icones = {"x": '<path d="M1 1"/>'}
        for remplace in ({"viewBox": "0 0 24 20"}, {"stroke-linecap": "butt"}, {"fill": "currentColor"}):
            tables = {"attributsTrace": {"valeur": {**TRAIT["attributsTrace"]["valeur"], **remplace}}}
            with self.assertRaises(TableInattendue, msg=repr(remplace)):
                g.kotlin(icones, tables, "t")
        for mauvais in ({}, {"Majuscule": '<path d="M1 1"/>'}, []):
            with self.assertRaises(TableInattendue, msg=repr(mauvais)):
                g.kotlin(mauvais, TRAIT, "t")


if __name__ == "__main__":
    unittest.main()
