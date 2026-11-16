#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
"""Tests du générateur des textes du pied de page :
`python3 -I scripts/test_generer_tables_apropos.py`."""

import json
import pathlib
import sys
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).parent))
import generer_tables_apropos as g  # noqa: E402
from generer_tables import TableInattendue  # noqa: E402

RACINE = pathlib.Path(__file__).resolve().parent.parent


def t(v):
    return {"source": "test", "valeur": v}


def tables(**remplace):
    base = {
        "plateforme": t({c: f"<{c}>" for c in g.PLATEFORME}),
        "elementsPied": t([{"code": c, "libelle": f"Lien {c}", "lien": {"infobulle": "Une aide"} if c == "site" else {}}
                           for c in g.LIENS]),
        "clesPied": t({"anneeDebut": "pied_de_page_annee_debut", "texte": "pied_de_page_texte"}),
    }
    base.update(remplace)
    return base


class TextesDuPied(unittest.TestCase):
    def test_les_trois_tables(self):
        texte = g.kotlin(tables(), "test.json")
        self.assertIn('const val licenceNom = "<licenceNom>"', texte)
        self.assertIn('val site = LienDuPied("Lien site", "Une aide")', texte)
        self.assertIn('val mentions = LienDuPied("Lien mentions", "")', texte)
        self.assertIn('const val cleAnneeDebut = "pied_de_page_annee_debut"', texte)

    def test_ce_qui_change_de_forme_arrete(self):
        for casse in ({"plateforme": t({"nom": "x"})}, {"elementsPied": t([])}, {"clesPied": t({})},
                      {"elementsPied": t([{"code": c} for c in g.LIENS])}):
            with self.assertRaises(TableInattendue, msg=str(casse)):
                g.kotlin(tables(**casse), "t")

    def test_le_plancher_se_genere(self):
        publiees = json.loads((RACINE / "contrat" / "tables-clients.json").read_text(encoding="utf-8"))["tables"]
        texte = g.kotlin(publiees, "tables-clients.json")
        for attendu in ('const val depotSource = "https://github.com/', 'val source = LienDuPied("Logiciel libre"',
                        'const val cleTexte = "pied_de_page_texte"'):
            self.assertIn(attendu, texte)


if __name__ == "__main__":
    unittest.main()
