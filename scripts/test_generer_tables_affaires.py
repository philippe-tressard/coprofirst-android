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
        "groupesSuivi": t([{"value": "en_cours", "label": "🟡 En cours", "statuts": ["en_ag", "en_cours"]}]),
        "colonnesKanban": t([{"id": "syndic", "label": "Syndic", "color": "var(--kanban-syndic)"}]),
        "motColonneVide": t("Aucune affaire"),
        "categorieActualite": t("actualite"),
        "optionsTicket": t(["epingle"]),
        "optionsPublication": t([{"cle": "epingle", "action": "Épingler", "etat": "Épinglée", "aide": "En tête."},
                                 {"cle": "brouillon", "action": "Réserver", "etat": "CS", "aide": "Au conseil."}]),
        "libellesModeSaisie": t({"essentielle": "Saisie essentielle", "complete": "Saisie complète"}),
    }
    base.update(remplace)
    return base


class TablesAffaires(unittest.TestCase):
    def test_les_tables(self):
        texte = g.kotlin(tables(), "test.json")
        # Le groupe du filtre perd son émoji, comme l'iOS.
        self.assertIn('GroupeDeSuivi("en_cours", "En cours", setOf("en_ag", "en_cours")),', texte)
        # La teinte d'une colonne est un JETON de la palette, jamais une couleur.
        self.assertIn('"syndic" to { it.kanbanSyndic },', texte)
        # La pastille du statut est commune (StatutsAffaire, generer_tables.py, #92).
        self.assertNotIn("pastillesStatut", texte)
        self.assertIn('const val COLONNE_VIDE = "Aucune affaire"', texte)
        # Les options du site pour une affaire, et elles seules.
        self.assertIn('OptionAffaire("epingle", "Épingler", "Épinglée", "En tête."),', texte)
        self.assertNotIn('"brouillon"', texte)

    def test_les_libelles_du_mode_de_saisie(self):
        """#229 : « Saisie essentielle », « Saisie complète » se lisent dans la table publiée."""
        self.assertIn('"essentielle" to "Saisie essentielle",', g.kotlin(tables(), "t"))
        for mauvais in (None, {}, {"essentielle": ""}, ["Saisie essentielle"]):
            with self.assertRaises(g.TableInattendue, msg=repr(mauvais)):
                g.kotlin(tables(libellesModeSaisie=t(mauvais)), "t")

    def test_ce_qui_change_de_forme_arrete(self):
        for casse in ({"groupesSuivi": t([{"value": "x", "label": "X"}])},
                      {"colonnesKanban": t([{"id": "ag", "color": "#123456"}])},
                      {"motColonneVide": t(None)},
                      {"optionsTicket": t(["inconnue"])}):
            with self.assertRaises(g.TableInattendue, msg=str(casse)):
                g.kotlin(tables(**casse), "t")

    def test_le_plancher_se_genere(self):
        publiees = json.loads((RACINE / "contrat" / "tables-clients.json").read_text(encoding="utf-8"))["tables"]
        texte = g.kotlin(publiees, "tables-clients.json")
        for attendu in ('"annule" to { it.kanbanAnnule }', 'OptionAffaire("urgente"'):
            self.assertIn(attendu, texte)


if __name__ == "__main__":
    unittest.main()
