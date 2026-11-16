#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
"""Tests du générateur de la capsule du verdict (#71) :
`python3 -I scripts/test_generer_tables_residence.py`."""

import json
import pathlib
import sys
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).parent))
import generer_tables_residence as g  # noqa: E402
from generer_tables import TableInattendue  # noqa: E402

RACINE = pathlib.Path(__file__).resolve().parent.parent


def tables(par_ton=None, inconnu="badge-yellow"):
    par_ton = {"succes": "badge-green", "danger": "badge-red"} if par_ton is None else par_ton
    return {"badgesDuVerdict": {"source": "test", "valeur": {"parTon": par_ton, "inconnu": inconnu}}}


class TablesReglement(unittest.TestCase):
    def test_la_table_publiee(self):
        publiees = json.loads((RACINE / "contrat" / "tables-clients.json").read_text(encoding="utf-8"))["tables"]
        texte = g.kotlin(publiees, "tables-clients.json")
        for attendu in ('"succes" to "badge-green",', '"danger" to "badge-red",', '"alerte" to "badge-orange",',
                        '"neutre" to "badge-gray",', 'const val BADGE_INCONNU = "badge-yellow"'):
            self.assertIn(attendu, texte)

    def test_une_forme_inattendue_arrete(self):
        for mauvais in (tables({}), tables({"succes": "badge-rose"}), tables(inconnu="jaune"),
                        tables({"Succès": "badge-green"}), {"badgesDuVerdict": {"valeur": ["badge-green"]}}, {}):
            with self.assertRaises(TableInattendue, msg=repr(mauvais)):
                g.kotlin(mauvais, "t")

    def test_la_table_tenue_ne_revient_pas(self):
        """#71 : la table écrite à la main, aux valeurs de l'iOS, a cédé la place à la génération."""
        modeles = (RACINE / "app/src/main/java/fr/coprofirst/app/fonctions/residence/ModelesReglement.kt")
        self.assertNotIn("object TablesReglement", modeles.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
