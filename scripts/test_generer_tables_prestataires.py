#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
"""Tests du générateur des tables de « Prestataires » :
`python3 -I scripts/test_generer_tables_prestataires.py`."""

import json
import pathlib
import sys
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).parent))
import generer_tables_prestataires as g  # noqa: E402

RACINE = pathlib.Path(__file__).resolve().parent.parent


def t(v):
    return {"source": "test", "valeur": v}


def tables(**remplace):
    base = {
        "typesPrestataire": t([{"val": "travaux", "label": "🏗️ Travaux", "desc": "Interventions \"importantes\""}]),
        "filtresContrat": t([{"val": "sous_contrat", "label": "📄 Sous contrat"}]),
    }
    base.update(remplace)
    return base


class TablesPrestataires(unittest.TestCase):
    def test_les_deux_tables(self):
        texte = g.kotlin(tables(), "test.json")
        self.assertIn('TypePrestataire("travaux", "🏗️ Travaux", "Interventions \\"importantes\\""),', texte)
        self.assertIn('"sous_contrat" to "📄 Sous contrat",', texte)

    def test_ce_qui_change_de_forme_arrete(self):
        for casse in ({"typesPrestataire": t([])}, {"typesPrestataire": t([{"val": "x", "label": "X"}])},
                      {"filtresContrat": t(None)}, {"filtresContrat": t([{"label": "X"}])}):
            with self.assertRaises(g.TableInattendue, msg=str(casse)):
                g.kotlin(tables(**casse), "t")

    def test_le_plancher_se_genere(self):
        publiees = json.loads((RACINE / "contrat" / "tables-clients.json").read_text(encoding="utf-8"))["tables"]
        texte = g.kotlin(publiees, "tables-clients.json")
        for attendu in ('TypePrestataire("maintenance_depannage", ', 'TypePrestataire("gestion", ',
                        '"sans_contrat" to '):
            self.assertIn(attendu, texte)


if __name__ == "__main__":
    unittest.main()
