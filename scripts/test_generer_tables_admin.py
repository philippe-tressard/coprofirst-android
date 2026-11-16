#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
"""Tests du générateur des tables du Paramétrage :
`python3 -I scripts/test_generer_tables_admin.py`."""

import json
import pathlib
import sys
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).parent))
import generer_tables_admin as g  # noqa: E402

RACINE = pathlib.Path(__file__).resolve().parent.parent


def t(v):
    return {"source": "test", "valeur": v}


def tables(**remplace):
    base = {
        "libellesTache": t({"backup": "Sauvegarde quotidienne", "reliability": 'Contrôles "de" fiabilité'}),
        "statutsTache": t({"ok": {"libelle": "À jour", "aide": "Un rapport est arrivé.", "classe": "badge-green"}}),
    }
    base.update(remplace)
    return base


class TablesAdmin(unittest.TestCase):
    def test_les_deux_tables(self):
        texte = g.kotlin(tables(), "test.json")
        self.assertIn('"backup" to "Sauvegarde quotidienne",', texte)
        self.assertIn('"reliability" to "Contrôles \\"de\\" fiabilité",', texte)
        self.assertIn('"ok" to StatutTache("À jour", "Un rapport est arrivé.", "badge-green"),', texte)

    def test_ce_qui_change_de_forme_arrete(self):
        for casse in ({"libellesTache": t({})}, {"libellesTache": t({"a": 1})}, {"statutsTache": t([])},
                      {"statutsTache": t({"ok": {"libelle": "À jour"}})}, {"statutsTache": t(None)}):
            with self.assertRaises(g.TableInattendue, msg=str(casse)):
                g.kotlin(tables(**casse), "t")

    def test_le_plancher_se_genere(self):
        publiees = json.loads((RACINE / "contrat" / "tables-clients.json").read_text(encoding="utf-8"))["tables"]
        texte = g.kotlin(publiees, "tables-clients.json")
        for attendu in ('"maintenance" to ', '"backup" to ', '"ok" to StatutTache(', '"aucune_execution" to StatutTache('):
            self.assertIn(attendu, texte)


if __name__ == "__main__":
    unittest.main()
