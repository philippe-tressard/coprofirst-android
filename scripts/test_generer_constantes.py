#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
"""Tests du générateur des constantes du serveur :
`python3 -I scripts/test_generer_constantes.py`."""

import json
import pathlib
import sys
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).parent))
import generer_constantes as g  # noqa: E402
from generer_tables import TableInattendue  # noqa: E402

RACINE = pathlib.Path(__file__).resolve().parent.parent


def c(v):
    return {"source": "test", "quoi": "test", "valeur": v}


def constantes(**remplace):
    base = {
        "schemasDeLien": c(["http", "https", "mailto"]),
        "longueurMinMotDePasse": c(8),
        "noteMin": c(1), "noteMax": c(5), "noteRemarque": c(3), "notePubliable": c(4),
        "defautsPreferences": c({"mon_batiment_mail": True, "autres_batiments_mail": False}),
    }
    base.update(remplace)
    return base


class Constantes(unittest.TestCase):
    def test_toutes_les_constantes(self):
        texte = g.kotlin(constantes(), "test.json")
        self.assertIn('val schemasDeLien: Set<String> = setOf("http", "https", "mailto")', texte)
        self.assertIn("const val LONGUEUR_MIN_MOT_DE_PASSE = 8", texte)
        self.assertIn("const val NOTE_PUBLIABLE = 4", texte)
        self.assertIn('"autres_batiments_mail" to false,', texte)

    def test_ce_qui_change_de_forme_arrete(self):
        for casse in ({"schemasDeLien": c([])}, {"schemasDeLien": c(["javascript:"])}, {"longueurMinMotDePasse": c("8")},
                      {"longueurMinMotDePasse": c(True)}, {"noteRemarque": c(6)}, {"noteMax": None},
                      {"defautsPreferences": c({"x": 1})}):
            with self.assertRaises(TableInattendue, msg=str(casse)):
                g.kotlin({k: v for k, v in constantes(**casse).items() if v is not None}, "t")

    def test_le_plancher_se_genere(self):
        publiees = json.loads((RACINE / "contrat" / "constantes-clients.json").read_text(encoding="utf-8"))["constantes"]
        texte = g.kotlin(publiees, "constantes-clients.json")
        for attendu in ('"https"', "LONGUEUR_MIN_MOT_DE_PASSE = ", "NOTE_MIN = ", '"mon_batiment_mail" to '):
            self.assertIn(attendu, texte)

    def test_les_copies_ne_reviennent_pas(self):
        """#74 : ce que constantes-clients.json publie ne se recopie plus dans un générateur."""
        for script in (RACINE / "scripts").glob("generer_*.py"):
            source = script.read_text(encoding="utf-8")
            for copie in ("SCHEMAS_DE_LIEN =", "SEUILS_AVIS_TENUS"):
                self.assertNotIn(copie, source, script.name)


if __name__ == "__main__":
    unittest.main()
