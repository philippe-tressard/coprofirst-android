#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
"""Tests du convertisseur du logo : `python3 -I scripts/test_generer_logo.py`."""

import pathlib
import sys
import tempfile
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).parent))
import generer_logo as g  # noqa: E402

RACINE = pathlib.Path(__file__).resolve().parent.parent
TETE = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512">'


def generer(corps: str) -> dict[str, str]:
    with tempfile.TemporaryDirectory() as d:
        g.generer(TETE + corps + "</svg>", pathlib.Path(d))
        return {f.name: f.read_text(encoding="utf-8") for f in (pathlib.Path(d) / "drawable").iterdir()}


class Convertisseur(unittest.TestCase):
    def test_tuile_puis_dessin(self):
        sortie = generer('<rect width="512" height="512" rx="96" fill="#FFFFFF"/>'
                         '<g transform="translate(10 20) scale(0.5)" fill="#1E3A5F"><rect x="1" y="2" width="3" height="4"/></g>')
        self.assertIn('android:fillColor="#FFFFFFFF"', sortie["logo.xml"])
        self.assertIn('android:translateX="10"', sortie["logo.xml"])
        self.assertIn('android:scaleX="0.5"', sortie["logo.xml"])
        self.assertIn("M1,2H4V6H1Z", sortie["logo.xml"])
        # Le premier plan de l'icône n'a pas la tuile ; le fond en a la couleur.
        self.assertNotIn("#FFFFFFFF", sortie["ic_launcher_premier_plan.xml"])
        self.assertIn('#FF1E3A5F', sortie["ic_launcher_premier_plan.xml"])
        self.assertIn('android:fillColor="#FFFFFFFF"', sortie["ic_launcher_fond.xml"])

    def test_trait(self):
        sortie = generer('<rect width="512" height="512" fill="#FFFFFF"/>'
                         '<path d="M0 0H10" stroke="#C9983A" stroke-width="29" stroke-linecap="round"/>')
        self.assertIn('android:strokeColor="#FFC9983A"', sortie["logo.xml"])
        self.assertIn('android:strokeLineCap="round"', sortie["logo.xml"])
        # Le remplissage par défaut du SVG (noir) est conservé : sans effet sur une ligne.
        self.assertIn('android:fillColor="#FF000000"', sortie["logo.xml"])

    def test_ce_qui_est_inconnu_arrete(self):
        for corps in ('<rect width="1" height="1" fill="#FFF"/><circle r="3"/>',
                      '<rect width="1" height="1" fill="#FFFFFF" opacity="0.5"/>',
                      '<rect width="1" height="1" fill="url(#degrade)"/>',
                      '<rect width="1" height="1" fill="#FFFFFF"/><g transform="rotate(45)"/>'):
            with self.assertRaises(g.LogoInconnu, msg=corps):
                generer(corps)

    def test_le_logo_recopie_se_convertit(self):
        sortie = generer_fichier(RACINE / "app" / "src" / "main" / "logo" / "logo.svg")
        self.assertIn("#FF1E3A5F", sortie["logo.xml"])
        self.assertIn("#FFC9983A", sortie["logo.xml"])


def generer_fichier(chemin: pathlib.Path) -> dict[str, str]:
    with tempfile.TemporaryDirectory() as d:
        g.generer(chemin.read_text(encoding="utf-8"), pathlib.Path(d))
        return {f.name: f.read_text(encoding="utf-8") for f in (pathlib.Path(d) / "drawable").iterdir()}


if __name__ == "__main__":
    unittest.main(verbosity=1)
