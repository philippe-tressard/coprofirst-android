#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
"""Tests du générateur des composants tiers :
`python3 -I scripts/test_generer_composants_tiers.py`."""

import json
import pathlib
import sys
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).parent))
import generer_composants_tiers as g  # noqa: E402

RACINE = pathlib.Path(__file__).resolve().parent.parent
APACHE = "LICENSES/Apache-2.0.txt"


def composant(nom="OkHttp", modules=("com.squareup.okhttp3:*",), licences=("Apache-2.0",), textes=(APACHE,)):
    return {"nom": nom, "licences": list(licences), "role": "Le transport.", "modules": list(modules),
            "textes": list(textes)}


def verifier(*composants, modules=("com.squareup.okhttp3:okhttp",), existe=lambda t: True):
    return g.verifier({"composants": list(composants)}, list(modules), existe)


class ComposantsTiers(unittest.TestCase):
    def test_un_module_couvert_se_range_sous_son_composant(self):
        (c,) = verifier(composant(), modules=("com.squareup.okhttp3:okhttp", "com.squareup.okhttp3:okhttp-android"))
        self.assertEqual(c["modules"], ["com.squareup.okhttp3:okhttp", "com.squareup.okhttp3:okhttp-android"])
        self.assertEqual(c["textes"], ["Apache-2.0.txt"])

    def test_une_licence_hors_du_depot_a_son_texte_a_cote(self):
        (c,) = verifier(composant(licences=("MPL-2.0",), textes=("app/src/main/tiers/LICENSE-MPL-2.0.txt",)))
        self.assertEqual(c["textes"], ["LICENSE-MPL-2.0.txt"])

    def test_les_nomenclatures_sont_ecartees(self):
        self.assertEqual(g.embarques(["a:b-bom", "a:b", "", "a:b"]), ["a:b"])

    def test_ce_qui_ne_dit_pas_la_verite_arrete(self):
        cas = {
            "module sans composant": dict(modules=("com.squareup.okhttp3:okhttp", "org.inconnu:x")),
            "motif qui ne sert plus": dict(modules=()),
            "licence sans texte": dict(),
            "texte introuvable": dict(existe=lambda t: False),
        }
        for nom, options in cas.items():
            premier = composant(licences=("Apache-2.0", "MPL-2.0")) if nom == "licence sans texte" else composant()
            with self.assertRaises(g.DeclarationInvalide, msg=nom):
                verifier(premier, **options)
        with self.assertRaises(g.DeclarationInvalide, msg="deux composants"):
            verifier(composant(), composant("Autre", modules=("com.squareup.*:*",)))
        with self.assertRaises(g.DeclarationInvalide, msg="incomplet"):
            verifier({"nom": "X"})

    def test_la_declaration_du_depot_est_complete(self):
        """Chaque motif, licence et texte de la déclaration versionnée tient (hors Gradle)."""
        declaration = json.loads((RACINE / "app/src/main/tiers/composants.json").read_text(encoding="utf-8"))
        for c in declaration["composants"]:
            for t in c["textes"]:
                self.assertTrue((RACINE / t).is_file(), t)
            for licence in c["licences"]:
                self.assertTrue(set(g.textes_de_licence(licence)) & set(c["textes"]), c["nom"])


if __name__ == "__main__":
    unittest.main()
