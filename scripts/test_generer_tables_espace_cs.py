#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
"""Tests du générateur des tables de l'Espace CS :
`python3 -I scripts/test_generer_tables_espace_cs.py`."""

import json
import pathlib
import sys
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).parent))
import generer_tables_espace_cs as g  # noqa: E402

RACINE = pathlib.Path(__file__).resolve().parent.parent


def t(v):
    return {"source": "test", "valeur": v}


def tables(**remplace):
    base = {
        "verdictsReleve": t({"accepte": {"libelle": "Ajouté au fil", "ton": "succes"}}),
        "teinteDuTon": t({"succes": "badge-green"}),
        "formatsAffiche": t([{"val": "a4", "label": "A4"}]),
        "civilites": t(["Mme", "Mr"]),
        "teinteAge": t({"ancienne": "badge-red"}),
        "modesSuivi": t([{"valeur": "suivi", "libelle": "Suivi affaires"}]),
        "etatsVisite": t({"realisee": {"libelle": "✅ Réalisée", "teinte": "badge-green"}}),
        "motifsEvolution": t({"etat": "Changement d’état"}),
        "motifEvolutionParDefaut": t("Du nouveau"),
        "jetonsCharte": t({"--serie-1": "#000", "--palier-2": "#111", "--color-neutre-fond": "#222"}),
        "tranchesRepartition": t({
            "s1": {"background": "var(--serie-1)"},
            "plusieurs": {"background": "repeating-linear-gradient(45deg, var(--palier-2) 0 2px, var(--color-neutre-fond) 2px 5px)"},
        }),
    }
    base.update(remplace)
    return base


class TablesEspaceCS(unittest.TestCase):
    def test_les_tables(self):
        texte = g.kotlin(tables(), "test.json")
        self.assertIn('"accepte" to LibelleTeinte("Ajouté au fil", "badge-green"),', texte)
        self.assertIn('"a4" to "A4",', texte)
        self.assertIn('listOf("Mme", "Mr")', texte)
        self.assertIn('"ancienne" to "badge-red",', texte)
        self.assertIn('"suivi" to "Suivi affaires",', texte)
        self.assertIn('"realisee" to LibelleTeinte("Réalisée", "badge-green"),', texte)  # sans l'émoji
        self.assertIn('MOTIF_EVOLUTION_PAR_DEFAUT: String = "Du nouveau"', texte)
        self.assertNotIn("teintesStatutAffaire", texte)  # commune : StatutsAffaire (#92)
        self.assertIn('"s1" to listOf({ it.serie1 }),', texte)
        self.assertIn('"plusieurs" to listOf({ it.palier2 }, { it.neutreFond }),', texte)

    def test_ce_qui_change_de_forme_arrete(self):
        for casse in (
            {"verdictsReleve": t({"accepte": {"libelle": "x", "ton": "inconnu"}})},
            {"formatsAffiche": t([])},
            {"civilites": t(None)},
            {"etatsVisite": t({"realisee": {"libelle": "x"}})},
            {"tranchesRepartition": t({"s9": {"background": "var(--serie-9)"}})},  # jeton absent de la charte
            {"tranchesRepartition": t({"s1": {"background": "#fff"}})},
        ):
            with self.assertRaises(g.TableInattendue, msg=str(casse)):
                g.kotlin(tables(**casse), "t")

    def test_le_plancher_se_genere(self):
        publiees = json.loads((RACINE / "contrat" / "tables-clients.json").read_text(encoding="utf-8"))["tables"]
        texte = g.kotlin(publiees, "tables-clients.json")
        for attendu in ('"a_classer" to ', '"a7" to ', '"Mlle"', '"relance" to "Relancer le syndic"',
                        '"annulee" to LibelleTeinte(', '"autre" to listOf({ it.textMuted })'):
            self.assertIn(attendu, texte)


if __name__ == "__main__":
    unittest.main()
