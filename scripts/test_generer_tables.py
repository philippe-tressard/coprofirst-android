#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
"""Tests du générateur des tables : `python3 -I scripts/test_generer_tables.py`."""

import json
import pathlib
import sys
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).parent))
import generer_tables as g  # noqa: E402

RACINE = pathlib.Path(__file__).resolve().parent.parent


def t(v):
    return {"source": "test", "valeur": v}


DROITS = {"rolesDuDroit": t({"admin": ["admin"], "operateur": ["operateur"], "proprietaire": ["propriétaire"],
                             "adminSeul": ["admin"], "sansAdminSeul": ["résident"]}),
          "statutsDuProfil": t({"locataire": ["locataire"], "gestionnaire": ["syndic"]})}


class Tables(unittest.TestCase):
    def tables(self, pages):
        return {"pages": t(pages), "titreFil": t("Fil d'activité"), **DROITS}

    def test_pages_onglets_et_droits(self):
        texte = g.kotlin(self.tables([
            {"id": "residence", "href": "/residence", "navLabel": "Résidence", "titre": "Ma résidence", "icone": "building-2",
             "onglets": [{"id": "fiche", "label": "📋 Fiche"}, {"id": "carnet", "label": "Carnet", "reserve": "proprioOuCS"}]},
            {"id": "admin", "href": "/admin", "navLabel": "Admin", "titre": "Paramétrage", "icone": "sliders",
             "reserveeAdmin": True, "onglets": [{"id": "telemetry", "label": "Télémétrie", "ouvertOperateur": True}]},
            {"id": "profil", "href": None, "navLabel": "Profil", "titre": "Profil", "icone": "user"},
        ]), "test.json")
        self.assertIn('PageDeNavigation("residence", "/residence", "Résidence", "Ma résidence", "building-2"),', texte)
        self.assertNotIn('PageDeNavigation("profil"', texte)
        self.assertIn('OngletDuSite("fiche", "Fiche", null, false),', texte)
        self.assertIn('OngletDuSite("carnet", "Carnet", ReserveOnglet.ProprioOuCS, false),', texte)
        self.assertIn('OngletDuSite("telemetry", "Télémétrie", null, true),', texte)
        self.assertIn('val pagesReserveesAdmin: Set<String> = setOf("admin")', texte)
        self.assertIn('const val TITRE_DU_FIL: String = "Fil d\'activité"', texte)

    def test_ce_qui_change_de_forme_arrete(self):
        with self.assertRaises(g.TableInattendue):
            g.kotlin(self.tables([{"id": "x", "href": "/x", "navLabel": "X", "titre": "X", "icone": "i",
                                   "onglets": [{"id": "o", "label": "O", "reserve": "nouvelleReserve"}]}]), "t")
        with self.assertRaises(g.TableInattendue):
            g.kotlin({"pages": t([]), "titreFil": t(""), **DROITS}, "t")

    def test_echappement(self):
        self.assertEqual(g.chaine('a"b$c'), '"a\\"b\\$c"')

    def test_le_plancher_se_genere(self):
        tables = json.loads((RACINE / "contrat" / "tables-clients.json").read_text(encoding="utf-8"))["tables"]
        texte = g.kotlin(tables, "tables-clients.json")
        for page in ("tableau-de-bord", "residence", "mes-demandes", "espace-cs", "admin", "delegations"):
            self.assertIn(f'PageDeNavigation("{page}"', texte)


if __name__ == "__main__":
    unittest.main(verbosity=1)
