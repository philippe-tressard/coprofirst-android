#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
"""Tests du juge des réponses simulées : `python3 -I scripts/test_verifier_fixtures.py`."""

import pathlib
import sys
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).parent))
import verifier_fixtures as v  # noqa: E402

SCHEMAS = {
    "Lot": {"type": "object", "required": ["id", "type"], "properties": {
        "id": {"type": "integer"}, "type": {"type": "string", "enum": ["appartement", "cave"]},
        "etage": {"anyOf": [{"type": "integer"}, {"type": "null"}]}}},
}


def fichier(declaration: str, corps: str) -> dict[str, str]:
    return {"F.kt": f'    /** GET /lots {declaration}. */\n    const val LOTS = """{corps}"""\n'}


class JugeDesFixtures(unittest.TestCase):
    def test_conforme(self):
        self.assertEqual(v.verifier(fichier("→ `[Lot]`", '[{"id": 1, "type": "cave", "etage": null}]'), SCHEMAS), [])
        self.assertEqual(v.verifier(fichier("→ erreur", '{"detail": "Non."}'), SCHEMAS), [])

    def test_ce_que_le_serveur_ne_sert_pas_echoue(self):
        cas = {
            "champ inconnu": ("→ `Lot`", '{"id": 1, "type": "cave", "nom": "x"}'),
            "requis absent": ("→ `Lot`", '{"id": 1}'),
            "hors énumération": ("→ `Lot`", '{"id": 1, "type": "garage"}'),
            "type faux": ("→ `Lot`", '{"id": "1", "type": "cave"}'),
            "booléen pour un entier": ("→ `Lot`", '{"id": true, "type": "cave"}'),
            "liste pour un objet": ("→ `Lot`", '[{"id": 1, "type": "cave"}]'),
            "sans déclaration": ("", '{"id": 1, "type": "cave"}'),
            "schéma inconnu": ("→ `Inconnu`", "{}"),
            "erreur sans detail": ("→ erreur", '{"message": "x"}'),
            "modèle Kotlin": ("→ `Lot`", '{"id": $id, "type": "cave"}'),
            "JSON illisible": ("→ `Lot`", "{id: 1}"),
        }
        for nom, (declaration, corps) in cas.items():
            self.assertTrue(v.verifier(fichier(declaration, corps), SCHEMAS), nom)

    def test_une_reponse_du_meme_fichier_se_cite(self):
        texte = ('    /** → `Lot`. */\n    const val A = """{"id": 1, "type": "cave"}"""\n'
                 '    /** → `[Lot]`. */\n    const val B = """[$A, ${A}]"""\n')
        self.assertEqual(v.verifier({"F.kt": texte}, SCHEMAS), [])

    def test_le_depot_est_conforme(self):
        self.assertEqual(v.main(["x"]), 0)


if __name__ == "__main__":
    unittest.main()
