#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
"""Tests du générateur des tables des « Délégations aidant » :
`python3 -I scripts/test_generer_tables_delegations.py`."""

import json
import pathlib
import sys
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).parent))
import generer_tables_delegations as g  # noqa: E402

RACINE = pathlib.Path(__file__).resolve().parent.parent


def t(v):
    return {"source": "test", "valeur": v}


def tables(**remplace):
    base = {
        "pages": t([{"id": "profil"}, {"id": "delegations", "descriptif": 'Un proche "aidant".'}]),
        "statutsDelegation": t({"en_attente": {"libelle": "En attente", "badge": "badge-orange"}}),
    }
    base.update(remplace)
    return base


class TablesDelegations(unittest.TestCase):
    def test_le_descriptif_et_les_etats(self):
        texte = g.kotlin(tables(), "test.json")
        self.assertIn('const val DESCRIPTIF: String = "Un proche \\"aidant\\"."', texte)
        self.assertIn('"en_attente" to LibelleTeinte("En attente", "badge-orange"),', texte)

    def test_ce_qui_change_de_forme_arrete(self):
        for casse in ({"pages": t([{"id": "profil"}])}, {"pages": t([{"id": "delegations"}])},
                      {"statutsDelegation": t([])}, {"statutsDelegation": t({"active": {"libelle": "Active"}})},
                      {"statutsDelegation": t(None)}):
            with self.assertRaises(g.TableInattendue, msg=str(casse)):
                g.kotlin(tables(**casse), "t")

    def test_le_plancher_se_genere(self):
        publiees = json.loads((RACINE / "contrat" / "tables-clients.json").read_text(encoding="utf-8"))["tables"]
        texte = g.kotlin(publiees, "tables-clients.json")
        for attendu in ("proches aidants", '"en_attente" to LibelleTeinte(', '"active" to ', '"revoquee" to ',
                        '"expiree" to '):
            self.assertIn(attendu, texte)


if __name__ == "__main__":
    unittest.main()
