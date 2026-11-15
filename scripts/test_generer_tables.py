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

    def test_tables_communes(self):
        tables = {
            "attributsStatut": t({"locataire": {"libelle": "Locataire", "abrege": "Loc.", "badge": "badge-purple"}}),
            "attributsRole": t({"admin": {"libelle": "Admin", "badge": "badge-orange"}}),
            "libellesHerites": t({"bailleur": "Copropriétaire bailleur"}),
            "optionsTheme": t([{"val": "auto", "label": "Automatique", "desc": "Suit l'appareil"}, {"val": "sombre", "label": "Sombre"}]),
            "themeParDefaut": t("auto"), "cookieTheme": t("theme"),
        }
        texte = g.kotlin_commun(tables, "t")
        self.assertIn('"locataire" to AttributRole("Locataire", "badge-purple", "Loc."),', texte)
        self.assertIn('"admin" to AttributRole("Admin", "badge-orange", null),', texte)
        self.assertIn('OptionTheme("sombre", "Sombre", ""),', texte)
        self.assertIn('const val THEME_PAR_DEFAUT: String = "auto"', texte)
        with self.assertRaises(g.TableInattendue):
            g.kotlin_commun({**tables, "themeParDefaut": t("inconnu")}, "t")

    def test_echappement(self):
        self.assertEqual(g.chaine('a"b$c'), '"a\\"b\\$c"')

    def test_le_plancher_se_genere(self):
        tables = json.loads((RACINE / "contrat" / "tables-clients.json").read_text(encoding="utf-8"))["tables"]
        g.kotlin_commun(tables, "tables-clients.json")
        texte = g.kotlin(tables, "tables-clients.json")
        for page in ("tableau-de-bord", "residence", "mes-demandes", "espace-cs", "admin", "delegations"):
            self.assertIn(f'PageDeNavigation("{page}"', texte)
        balises = g.kotlin_balises(tables, "tables-clients.json")
        self.assertIn("package fr.coprofirst.app.commun.texte", balises)
        for attendu in ('"strong"', '"li"', '"a"', '"summary"'):
            self.assertIn(attendu, balises)
        self.assertNotIn('"table"', balises)
        self.assertNotIn('"script"', balises)


class Balises(unittest.TestCase):
    BALISES = ["p", "br", "b", "i", "u", "s", "strong", "em", "ul", "ol", "li", "a", "h1"]

    def tables(self, balises=None, attributs=None):
        return {"balisesAdmises": t(self.BALISES if balises is None else balises),
                "attributsAdmis": t(["href", "alt"] if attributs is None else attributs)}

    def test_liste_blanche_et_schemas(self):
        texte = g.kotlin_balises(self.tables(), "test.json")
        self.assertIn('val balises: Set<String> = setOf("a", "b", "br", "em", "h1", "i", "li", "ol", "p", "s", '
                      '"strong", "u", "ul")', texte)
        self.assertIn('val attributs: Set<String> = setOf("alt", "href")', texte)
        # Les schémas de l'iOS (`SCHEMAS_URL` de l'API) : ni javascript, ni data, ni tel.
        self.assertIn('val schemasDeLien: Set<String> = setOf("http", "https", "mailto")', texte)

    def test_une_forme_inattendue_arrete(self):
        for mauvaise in (self.tables(balises="p"), self.tables(balises=["p", "br"]),
                         self.tables(balises=self.BALISES + ["P"]), self.tables(balises=self.BALISES + ["p"]),
                         self.tables(balises=self.BALISES + [3]), self.tables(attributs=["alt"]),
                         self.tables(attributs=[]), {"balisesAdmises": t(self.BALISES)}):
            with self.assertRaises(g.TableInattendue, msg=repr(mauvaise)):
                g.kotlin_balises(mauvaise, "t")


if __name__ == "__main__":
    unittest.main(verbosity=1)
