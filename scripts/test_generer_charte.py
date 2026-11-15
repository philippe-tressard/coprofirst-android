#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
"""Tests du générateur des palettes : `python3 -I scripts/test_generer_charte.py`."""

import json
import pathlib
import sys
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).parent))
import generer_charte as g  # noqa: E402

RACINE = pathlib.Path(__file__).resolve().parent.parent


def table(valeur):
    return {"source": "test", "valeur": valeur}


class Resolution(unittest.TestCase):
    def test_hexa_rgba_var_et_boucle(self):
        t = {"a": "#102030", "b": "var(--a)", "c": "rgba(0, 0, 0, 0.45)", "d": "var(--d)", "e": "color-mix(x)"}
        self.assertEqual(g.resoudre("a", t), (0x10, 0x20, 0x30, 1.0))
        self.assertEqual(g.resoudre("b", t), (0x10, 0x20, 0x30, 1.0))
        self.assertEqual(g.resoudre("c", t), (0, 0, 0, 0.45))
        self.assertIsNone(g.resoudre("d", t))
        self.assertIsNone(g.resoudre("e", t))
        self.assertIsNone(g.resoudre("absent", t))

    def test_noms_et_couleurs(self):
        self.assertEqual(g.propriete("color-primary-plein"), "primaryPlein")
        self.assertEqual(g.propriete("fil-rouge-fond"), "filRougeFond")
        self.assertEqual(g.propriete("serie-1"), "serie1")
        self.assertEqual(g.argb((0x10, 0x20, 0x30, 1.0)), "Color(0xFF102030)")
        self.assertEqual(g.argb((0, 0, 0, 0.45)), "Color(0x73000000)")


class Cascade(unittest.TestCase):
    TABLES = {
        "jetonsCharte": table({"--color-bg": "#ffffff", "--color-text": "#000000", "--teinte": "#111111",
                               "--kanban-ag": "var(--teinte)", "--color-mix": "color-mix(in srgb, red 5%, black)",
                               "color": "var(--color-text)"}),
        "jetonsCharteSombre": table({"--color-bg": "#0d1b2a", "--color-text": "#e8e6e1"}),
        "palettesClaires": table({"prune": {"--color-bg": "#f0e0f0", "--color-text": "#200020"}}),
        "palettesSombres": table({"prune": {"--color-bg": "#100010"}}),
    }

    def test_cascade_du_site(self):
        p = g.cascade(self.TABLES)
        self.assertEqual(set(p), {"seine", "prune"})
        clair, sombre = p["prune"]
        self.assertEqual(clair["color-bg"], "#f0e0f0")
        # Au sombre : la charte sombre passe par-dessus la palette claire…
        self.assertEqual(sombre["color-text"], "#e8e6e1")
        # … et la palette sombre par-dessus tout.
        self.assertEqual(sombre["color-bg"], "#100010")

    def test_portables_et_ecartes(self):
        p = g.cascade(self.TABLES)
        gardes, ecartes = g.portables(p["seine"][0], p)
        self.assertEqual(gardes, ["color-bg", "color-text", "kanban-ag"])
        self.assertEqual(ecartes, ["color-mix"])

    def test_kotlin(self):
        texte = g.kotlin(self.TABLES, "test.json")
        self.assertTrue(texte.startswith("// GÉNÉRÉ par scripts/generer_charte.py"))
        self.assertIn("val kanbanAg: Color,", texte)
        self.assertIn('"prune" to Palette(', texte)
        self.assertIn("Color(0xFF100010)", texte)


class TablesPubliees(unittest.TestCase):
    """Les tables recopiées du plancher se génèrent : une forme changée échoue ici."""

    def test_le_plancher_se_genere(self):
        tables = json.loads((RACINE / "contrat" / "tables-clients.json").read_text(encoding="utf-8"))["tables"]
        p = g.cascade(tables)
        self.assertIn("seine", p)
        self.assertGreaterEqual(len(p), 2)
        gardes, _ = g.portables(p["seine"][0], p)
        for attendu in ("color-primary", "color-text", "color-text-muted", "color-bg", "color-surface", "color-danger"):
            self.assertIn(attendu, gardes)


if __name__ == "__main__":
    unittest.main(verbosity=1)
