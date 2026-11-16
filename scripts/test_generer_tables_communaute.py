#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
"""Tests du générateur des tables de la Communauté :
`python3 -I scripts/test_generer_tables_communaute.py`."""

import json
import pathlib
import sys
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).parent))
import generer_tables_communaute as g  # noqa: E402

RACINE = pathlib.Path(__file__).resolve().parent.parent


def t(v):
    return {"source": "test", "valeur": v}


def tables(**remplace):
    base = {
        "statutsIdee": t([{"value": "ouverte", "label": "💡 Ouverte", "badge": "badge-blue"}]),
        "typesAnnonce": t([{"val": "vente", "label": "🏷️ Vente"}]),
        "categoriesAnnonce": t([{"val": "divers", "label": "📦 Divers"}]),
        "trisAnnonce": t([{"val": "recent", "label": "Plus récentes"}]),
        "statutsAnnonce": t([{"val": "en_cours", "label": "En cours"}]),
        "teinteStatutAnnonce": t({"en_cours": "badge-green"}),
    }
    base.update(remplace)
    return base


class TablesCommunaute(unittest.TestCase):
    def test_les_tables(self):
        texte = g.kotlin(tables(), "test.json")
        self.assertIn('"ouverte" to LibelleTeinte("💡 Ouverte", "badge-blue"),', texte)
        self.assertIn('"vente" to "🏷️ Vente",', texte)
        self.assertIn('"divers" to "📦 Divers",', texte)
        self.assertIn('"recent" to "Plus récentes",', texte)
        self.assertIn('"en_cours" to LibelleTeinte("En cours", "badge-green"),', texte)
        # Les seuils de l'avis sont des constantes du serveur (generer_constantes.py, #74).
        self.assertNotIn("NOTE_", texte)

    def test_ce_qui_change_de_forme_arrete(self):
        for casse in ({"statutsIdee": t([{"value": "x", "label": "X"}])},
                      {"statutsAnnonce": t([{"val": "vendu", "label": "Vendu"}])},
                      {"teinteStatutAnnonce": t([])}, {"typesAnnonce": t([])}, {"trisAnnonce": t(None)}):
            with self.assertRaises(g.TableInattendue, msg=str(casse)):
                g.kotlin(tables(**casse), "t")

    def test_le_plancher_se_genere(self):
        publiees = json.loads((RACINE / "contrat" / "tables-clients.json").read_text(encoding="utf-8"))["tables"]
        texte = g.kotlin(publiees, "tables-clients.json")
        for attendu in ('"realisee" to LibelleTeinte(', '"recherche" to ', '"parking_cave" to ', '"prix_desc" to ',
                        '"reserve" to LibelleTeinte("Réservé", "badge-orange"),'):
            self.assertIn(attendu, texte)


if __name__ == "__main__":
    unittest.main()
