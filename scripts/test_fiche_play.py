#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
"""La fiche du Play Store tient dans les limites de Google (#31) :
`python3 -I scripts/test_fiche_play.py`.

Nom : 30 caractères ; description courte : 80 ; description complète : 4000. Une fiche
qui déborde serait refusée à la saisie : elle se refuse ici, avant. Et elle ne promet
pas ce que l'app ne tient pas : la mesure d'audience existe, elle se dit.
"""

import pathlib
import re
import unittest

RACINE = pathlib.Path(__file__).resolve().parent.parent
FICHE = RACINE / "docs" / "play-store" / "fiche.md"
LIMITES = {"nom": 30, "courte": 80, "complete": 4000}


def champs(texte: str) -> dict[str, str]:
    """Chaque bloc ``` qui suit une marque `<!-- champ:nom -->`, sans ses retours finaux."""
    return {m.group(1): m.group(2).rstrip("\n")
            for m in re.finditer(r"<!-- champ:([a-z]+) -->\s*```\n(.*?)```", texte, flags=re.S)}


class FichePlay(unittest.TestCase):
    def setUp(self):
        self.champs = champs(FICHE.read_text(encoding="utf-8"))

    def test_chaque_champ_est_la_et_tient_dans_sa_limite(self):
        self.assertEqual(set(self.champs), set(LIMITES))
        for nom, limite in LIMITES.items():
            self.assertTrue(self.champs[nom].strip(), nom)
            self.assertLessEqual(len(self.champs[nom]), limite, nom)

    def test_rien_d_apple_ni_de_promesse_fausse(self):
        complete = self.champs["complete"]
        for mot in ("Face ID", "iPhone", "iPad", "App Store", "aucune mesure d'audience"):
            self.assertNotIn(mot, complete)
        self.assertIn("refuser dans votre profil", complete)

    def test_un_champ_trop_long_se_voit(self):
        trop = champs("<!-- champ:nom -->\n```\n" + "x" * 31 + "\n```")
        self.assertGreater(len(trop["nom"]), LIMITES["nom"])


if __name__ == "__main__":
    unittest.main()
