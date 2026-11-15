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
        # Sans entrée de menu : hors de la barre, mais connue (la cloche ouvre les notifications).
        toutes, hors_menu = texte.split("val horsMenu", 1)
        self.assertNotIn('PageDeNavigation("profil"', toutes)
        self.assertIn('PageDeNavigation("profil", "", "Profil", "Profil", "user"),', hors_menu)
        self.assertIn('OngletDuSite("fiche", "Fiche", null, false),', texte)
        self.assertIn('OngletDuSite("carnet", "Carnet", ReserveOnglet.ProprioOuCS, false),', texte)
        self.assertIn('OngletDuSite("telemetry", "Télémétrie", null, true),', texte)
        self.assertIn('val pagesReserveesAdmin: Set<String> = setOf("admin")', texte)
        self.assertIn('const val TITRE_DU_FIL: String = "Fil d\'activité"', texte)

    def test_descriptifs_et_adresses_des_onglets(self):
        texte = g.kotlin(self.tables([
            {"id": "admin", "href": "/admin", "navLabel": "Admin", "titre": "Paramétrage", "icone": "sliders",
             "onglets": [{"id": "imports", "label": "Imports", "route": "/admin?onglet=imports", "descriptif": "Charger"},
                         {"id": "nu", "label": "Nu"}]},
        ]), "test.json")
        descriptifs, routes = texte.split("val descriptifs", 1)[1].split("val routes", 1)
        self.assertIn('"admin" to mapOf(\n            "imports" to "Charger",\n        ),', descriptifs)
        self.assertIn('"imports" to "/admin?onglet=imports",', routes)
        self.assertNotIn('"nu" to', descriptifs + routes)

    def test_ce_qui_change_de_forme_arrete(self):
        with self.assertRaises(g.TableInattendue):
            g.kotlin(self.tables([{"id": "x", "href": "/x", "navLabel": "X", "titre": "X", "icone": "i",
                                   "onglets": [{"id": "o", "label": "O", "reserve": "nouvelleReserve"}]}]), "t")
        with self.assertRaises(g.TableInattendue):
            g.kotlin({"pages": t([]), "titreFil": t(""), **DROITS}, "t")
        for cle in ("route", "descriptif"):
            with self.assertRaises(g.TableInattendue, msg=cle):
                g.kotlin(self.tables([{"id": "x", "href": "/x", "navLabel": "X", "titre": "X", "icone": "i",
                                       "onglets": [{"id": "o", "label": "O", cle: 3}]}]), "t")
        with self.assertRaises(g.TableInattendue):
            g.kotlin(self.tables([{"id": "x", "href": None, "navLabel": "X", "titre": "X"}]), "t")

    def test_duree_de_l_avis(self):
        texte = g.kotlin_avis({"dureeToastMs": t(4000)}, "test.json")
        self.assertIn("package fr.coprofirst.app.commun", texte)
        self.assertIn("const val DUREE_MS: Long = 4000L", texte)
        for mauvaise in (0, -1, "4000", 4.5, True, None):
            with self.assertRaises(g.TableInattendue, msg=repr(mauvaise)):
                g.kotlin_avis({"dureeToastMs": t(mauvaise)}, "t")
        with self.assertRaises(g.TableInattendue):
            g.kotlin_avis({}, "t")

    def test_tables_communes(self):
        tables = {
            "attributsStatut": t({"locataire": {"libelle": "Locataire", "abrege": "Loc.", "badge": "badge-purple"}}),
            "attributsRole": t({"admin": {"libelle": "Admin", "badge": "badge-orange"}}),
            "rolesMisEnAvant": t(["admin"]),
            "statutsDemandables": t(["locataire"]),
            "libellesHerites": t({"bailleur": "Copropriétaire bailleur"}),
            "optionsTheme": t([{"val": "auto", "label": "Automatique", "desc": "Suit l'appareil"}, {"val": "sombre", "label": "Sombre"}]),
            "themeParDefaut": t("auto"), "cookieTheme": t("theme"),
        }
        texte = g.kotlin_commun(tables, "t")
        self.assertIn('"locataire" to AttributRole("Locataire", "badge-purple", "Loc."),', texte)
        self.assertIn('"admin" to AttributRole("Admin", "badge-orange", null),', texte)
        self.assertIn('OptionTheme("sombre", "Sombre", ""),', texte)
        self.assertIn('const val THEME_PAR_DEFAUT: String = "auto"', texte)
        self.assertIn('val rolesMisEnAvant: List<String> = listOf("admin")', texte)
        self.assertIn('val statutsDemandables: List<String> = listOf("locataire")', texte)
        with self.assertRaises(g.TableInattendue):
            g.kotlin_commun({**tables, "rolesMisEnAvant": t(["inconnu"])}, "t")
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
        self.assertIn('PageDeNavigation("notifications", ""', texte.split("val horsMenu", 1)[1])
        self.assertIn('"imports" to "/admin?onglet=imports",', texte)
        self.assertIn("const val DUREE_MS: Long = 4000L", g.kotlin_avis(tables, "tables-clients.json"))
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


class Natures(unittest.TestCase):
    # Une charte minimale : deux jetons portés, un écarté (color-mix), comme generer_charte les trie.
    CHARTE = {"jetonsCharte": t({"--fil-rouge": "#aa0000", "--fil-rouge-fond": "var(--fil-rouge)",
                                 "--color-primary": "#0000aa", "--color-mix": "color-mix(x)"}),
              "jetonsCharteSombre": t({}), "palettesClaires": t({}), "palettesSombres": t({})}

    def tables(self, rubriques=None, natures=None, teintes=None):
        return {**self.CHARTE,
                "naturesDuFil": t({"ticket_ouvert": {"libelle": "Affaire", "couleur": "var(--fil-rouge)",
                                                     "fond": "var(--fil-rouge-fond)", "icone": "wrench"}}
                                  if rubriques is None else rubriques),
                "naturesAffaire": t([{"val": "calendrier", "libelle": "Calendrier", "icone": "calendar-days"}]
                                    if natures is None else natures),
                "teinteNature": t({"calendrier": {"--nature-fort": "var(--color-violet)"}} if teintes is None else teintes)}

    def test_les_teintes_sont_des_jetons_de_couleurs(self):
        texte = g.kotlin_natures(self.tables(), "test.json")
        self.assertIn("package fr.coprofirst.app.charte", texte)
        self.assertIn('"ticket_ouvert" to NatureDuFil("Affaire", Couleurs::filRouge, Couleurs::filRougeFond, "wrench"),', texte)
        self.assertIn('NatureAffaire("calendrier", "Calendrier", "calendar-days", "badge-purple"),', texte)
        self.assertNotIn("Color(", texte)

    def test_une_forme_inattendue_arrete(self):
        rubrique = {"libelle": "Affaire", "couleur": "var(--fil-rouge)", "fond": "var(--fil-rouge-fond)", "icone": "wrench"}
        mauvaises = [
            self.tables(rubriques={}),
            self.tables(rubriques={"x": {**rubrique, "couleur": "#aa0000"}}),       # une couleur, pas un jeton
            self.tables(rubriques={"x": {**rubrique, "fond": "var(--fil-absent)"}}),  # un jeton que Couleurs n'a pas
            self.tables(rubriques={"x": {**rubrique, "couleur": "var(--color-mix)"}}),  # écarté par la charte
            self.tables(rubriques={"x": {**rubrique, "icone": ""}}),
            self.tables(rubriques={"x": "Affaire"}),
            self.tables(natures=[]),
            self.tables(natures=[{"val": "calendrier", "libelle": "Calendrier"}]),
            self.tables(teintes={}),
            self.tables(teintes={"calendrier": {"--nature-fort": "var(--color-rose)"}}),  # sans classe badge-*
            {k: v for k, v in self.tables().items() if k != "jetonsCharte"},
        ]
        for mauvaise in mauvaises:
            with self.assertRaises(g.TableInattendue, msg=repr(mauvaise)[:200]):
                g.kotlin_natures(mauvaise, "t")

    def test_le_plancher_se_genere(self):
        tables = json.loads((RACINE / "contrat" / "tables-clients.json").read_text(encoding="utf-8"))["tables"]
        texte = g.kotlin_natures(tables, "tables-clients.json")
        self.assertIn('"publication" to NatureDuFil("Actualité", Couleurs::primary, Couleurs::primaryLight, "newspaper"),', texte)
        self.assertIn('"annonce" to NatureDuFil("Petite annonce", Couleurs::orange, Couleurs::orangeFond, "heart-handshake"),', texte)
        for nature in ("actualite", "calendrier", "activite"):
            self.assertIn(f'NatureAffaire("{nature}"', texte)


class Fichiers(unittest.TestCase):
    def test_les_valeurs_de_l_ios(self):
        texte = g.kotlin_fichiers()
        self.assertIn("package fr.coprofirst.app.commun.pieces", texte)
        # Celles de la table générée de l'iOS (`FichiersDuSite.swift`) : `jpe?g`, `tiff?` dépliés.
        self.assertIn('val extensionsImage: Set<String> = setOf("avif", "bmp", "gif", "heic", "heif", "jpeg", '
                      '"jpg", "png", "tif", "tiff", "webp")', texte)
        self.assertIn("const val LONGUEUR_PREFIXE: Int = 32", texte)
        for cle, valeur in (("JPEG", "JPG"), ("XLS", "EXCEL"), ("DOCX", "WORD"), ("PPT", "POWERPOINT"), ("TXT", "TEXTE")):
            self.assertIn(f'"{cle}" to "{valeur}",', texte)
        self.assertNotIn('"pdf"', texte)

    def test_une_forme_inattendue_arrete(self):
        for mauvais in ({"extensions": ()}, {"extensions": ("JPG",)}, {"extensions": ("jpg", "jpg")},
                        {"extensions": ("j.pg",)}, {"prefixe": 0}, {"prefixe": "32"}, {"prefixe": True},
                        {"familles": ()}, {"familles": (("docx", "WORD"),)}, {"familles": (("DOC", "word"),)},
                        {"familles": (("DOC", "WORD"), ("DOC", "TEXTE"))}, {"familles": (("DOC", " "),)}):
            with self.assertRaises(g.TableInattendue, msg=repr(mauvais)):
                g.kotlin_fichiers(**mauvais)


if __name__ == "__main__":
    unittest.main(verbosity=1)
