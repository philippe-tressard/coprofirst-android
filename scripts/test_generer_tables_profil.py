#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
"""Tests du générateur des tables du Profil : `python3 -I scripts/test_generer_tables_profil.py`."""

import json
import pathlib
import sys
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).parent))
import generer_tables_profil as g  # noqa: E402
from generer_tables import TableInattendue  # noqa: E402

RACINE = pathlib.Path(__file__).resolve().parent.parent


def t(v):
    return {"source": "test", "valeur": v}


OPTIONS = [{"val": "essentielle", "label": "Essentielle", "desc": "Le strict"},
           {"val": "complete", "label": "Complète", "desc": "Tout"}]
CANAUX = [{"titre": "Par e-mail", "mon": "mon_mail", "autres": "autres_mail"},
          {"titre": "Dans l'application", "mon": "mon_app", "autres": "autres_app"}]


def tables(options=OPTIONS, canaux=CANAUX):
    return {"optionsSaisie": t(options), "canauxNotifications": t(canaux)}


class TablesProfil(unittest.TestCase):
    def test_les_saisies_dans_leur_ordre(self):
        texte = g.kotlin(tables(), "test.json")
        self.assertIn('OptionSaisie("essentielle", "Essentielle", "Le strict"),', texte)
        self.assertLess(texte.index('"essentielle"'), texte.index('"complete"'))

    def test_ce_qui_change_de_forme_arrete(self):
        for casse in (t([]), t(OPTIONS[:1]), t([{"val": "x", "label": "X"}, OPTIONS[1]]), t(None)):
            with self.assertRaises(TableInattendue, msg=str(casse)):
                g.kotlin({"optionsSaisie": casse, "canauxNotifications": t(CANAUX)}, "t")

    def test_les_canaux_dans_leur_ordre(self):
        texte = g.kotlin(tables(), "test.json")
        self.assertIn('CanalNotifications("Par e-mail", "mon_mail", "autres_mail"),', texte)
        self.assertIn('CanalNotifications("Dans l\'application", "mon_app", "autres_app"),', texte)
        self.assertLess(texte.index('"mon_mail"'), texte.index('"mon_app"'))

    def test_un_canal_qui_change_de_forme_arrete(self):
        doublon = [CANAUX[0], {"titre": "Bis", "mon": "mon_mail", "autres": "x"}]
        for casse in ([], [{"titre": "T", "mon": "m"}], [{"titre": "T", "mon": "m", "autres": 3}], doublon, None):
            with self.assertRaises(TableInattendue, msg=str(casse)):
                g.kotlin(tables(canaux=casse), "t")

    def test_le_plancher_se_genere(self):
        publiees = json.loads((RACINE / "contrat" / "tables-clients.json").read_text(encoding="utf-8"))["tables"]
        texte = g.kotlin(publiees, "tables-clients.json")
        self.assertIn('OptionSaisie("essentielle", "Essentielle",', texte)
        self.assertIn('OptionSaisie("complete", "Complète",', texte)
        self.assertIn('CanalNotifications("Notifications par e-mail", "mon_batiment_mail", "autres_batiments_mail"),', texte)


if __name__ == "__main__":
    unittest.main()
