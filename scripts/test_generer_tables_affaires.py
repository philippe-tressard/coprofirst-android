#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
"""Tests du générateur des tables de « Mes affaires » :
`python3 -I scripts/test_generer_tables_affaires.py`."""

import json
import pathlib
import sys
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).parent))
import generer_tables_affaires as g  # noqa: E402

RACINE = pathlib.Path(__file__).resolve().parent.parent


def t(v):
    return {"source": "test", "valeur": v}


def tables(**remplace):
    base = {
        "naturesAffaire": t([{"val": "actualite", "emoji": "📰", "libelle": "Actualité"}]),
        "groupesSuivi": t([{"value": "en_cours", "label": "🟡 En cours", "statuts": ["en_ag", "en_cours"]}]),
        "colonnesKanban": t([{"id": "syndic", "label": "Syndic", "color": "var(--kanban-syndic)"}]),
        "motColonneVide": t("Aucune affaire"),
        "categorieActualite": t("actualite"),
        "statutsTicket": t([{"value": "ouvert", "label": "Ouvert", "badge": "badge-blue"}]),
        "statutsTicketHistoriques": t({"fermé": {"label": "Fermé", "badge": "badge-gray"}}),
        "statutsTicketSansCycle": t({"publie": {"label": "Publiée", "badge": "badge-blue"}}),
    }
    base.update(remplace)
    return base


class TablesAffaires(unittest.TestCase):
    def test_les_tables(self):
        texte = g.kotlin(tables(), "test.json")
        self.assertIn('NatureAffaire("actualite", "Actualité", "📰"),', texte)
        # Le groupe du filtre perd son émoji, comme l'iOS.
        self.assertIn('GroupeDeSuivi("en_cours", "En cours", setOf("en_ag", "en_cours")),', texte)
        # La teinte d'une colonne est un JETON de la palette, jamais une couleur.
        self.assertIn('"syndic" to { it.kanbanSyndic },', texte)
        for code, badge in (("ouvert", "badge-blue"), ("fermé", "badge-gray"), ("publie", "badge-blue")):
            self.assertIn(f'"{code}" to "{badge}",', texte)
        self.assertIn('const val COLONNE_VIDE = "Aucune affaire"', texte)

    def test_ce_qui_change_de_forme_arrete(self):
        for casse in ({"naturesAffaire": t([{"val": "x"}])},
                      {"groupesSuivi": t([{"value": "x", "label": "X"}])},
                      {"colonnesKanban": t([{"id": "ag", "color": "#123456"}])},
                      {"statutsTicketHistoriques": t([])},
                      {"motColonneVide": t(None)}):
            with self.assertRaises(g.TableInattendue, msg=str(casse)):
                g.kotlin(tables(**casse), "t")

    def test_le_plancher_se_genere(self):
        publiees = json.loads((RACINE / "contrat" / "tables-clients.json").read_text(encoding="utf-8"))["tables"]
        texte = g.kotlin(publiees, "tables-clients.json")
        for attendu in ('"annule" to { it.kanbanAnnule }', '"chez_prestataire" to ', 'NatureAffaire("calendrier"'):
            self.assertIn(attendu, texte)


if __name__ == "__main__":
    unittest.main()
