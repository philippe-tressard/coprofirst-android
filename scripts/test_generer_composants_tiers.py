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


def verifier(*composants, modules=("com.squareup.okhttp3:okhttp",), existe=lambda t: True, autres=()):
    return g.verifier({"composants": list(composants)}, list(modules), existe, list(autres))


class ComposantsTiers(unittest.TestCase):
    def test_un_module_couvert_se_range_sous_son_composant(self):
        (c,) = verifier(composant(), modules=("com.squareup.okhttp3:okhttp", "com.squareup.okhttp3:okhttp-android"))
        self.assertEqual(c["modules"], ["com.squareup.okhttp3:okhttp", "com.squareup.okhttp3:okhttp-android"])
        self.assertEqual(c["textes"], ["Apache-2.0.txt"])

    def test_une_licence_hors_du_depot_a_son_texte_a_cote(self):
        (c,) = verifier(composant(licences=("MPL-2.0",), textes=("app/src/main/tiers/LICENSE-MPL-2.0.txt",)))
        self.assertEqual(c["textes"], ["LICENSE-MPL-2.0.txt"])

    def test_un_fichier_du_depot_embarque_est_un_composant(self):
        """#183 : les tracés des icônes du site ne sont pas un module Maven."""
        icones = {"nom": "Lucide", "licences": ["ISC"], "role": "Les tracés.", "fichiers": ["contrat/icones-svg.json"],
                  "textes": ["LICENSES/ISC.txt"]}
        _, c = verifier(composant(), icones)
        self.assertEqual(c["modules"], ["contrat/icones-svg.json"])
        with self.assertRaises(g.DeclarationInvalide):
            verifier(composant(), icones, existe=lambda t: t != "contrat/icones-svg.json")
        with self.assertRaises(g.DeclarationInvalide):
            verifier(composant(), {**icones, "fichiers": []})

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

    def test_le_graphe_se_lit_avec_ou_sans_requis(self):
        lignes = ["a:x\tb:y c:z", "b:y\t", "c:z", "d:w-bom\ta:x", ""]
        self.assertEqual(g.graphe(lignes), {"a:x": {"b:y", "c:z"}, "b:y": set(), "c:z": set(), "d:w-bom": {"a:x"}})
        self.assertEqual(g.embarques(lignes), ["a:x", "b:y", "c:z"])

    def test_un_motif_ne_meurt_que_s_il_ne_sert_a_aucune_des_deux_apps(self):
        """#317 : la déclaration est commune ; ZXing n'est que dans le téléphone."""
        zxing = composant("ZXing", modules=("com.google.zxing:core",))
        okhttp, zx = verifier(composant(), zxing, autres=("com.google.zxing:core",))  # la montre
        self.assertEqual(zx["modules"], ["com.google.zxing:core"])
        with self.assertRaises(g.DeclarationInvalide):
            verifier(composant(), zxing)  # aucune app ne l'embarque plus
        with self.assertRaises(g.DeclarationInvalide, msg="l'autre app ne couvre pas un module de celle-ci"):
            verifier(composant(), modules=("com.squareup.okhttp3:okhttp", "org.inconnu:x"), autres=("org.inconnu:x",))

    def test_une_bibliotheque_google_hors_licence_se_voit(self):
        """#320 : atteinte depuis les deux permises, ou refusée."""
        permises = {"com.google.firebase:firebase-messaging", "com.google.android.gms:play-services-wearable"}
        sain = g.graphe([
            "androidx.wear:wear-remote-interactions\tcom.google.android.gms:play-services-wearable",
            "com.google.android.gms:play-services-wearable\tcom.google.android.gms:play-services-basement",
            "com.google.android.gms:play-services-basement\t",
            "com.google.firebase:firebase-messaging\tcom.google.firebase:firebase-common",
            "com.google.firebase:firebase-common\t",
        ])
        self.assertEqual(g.google_non_permises(sain, permises), [])
        fautif = {**sain, "com.exemple:sdk": {"com.google.android.gms:play-services-ads"},
                  "com.google.android.gms:play-services-ads": {"com.google.android.gms:play-services-basement"}}
        self.assertEqual(g.google_non_permises(fautif, permises), ["com.google.android.gms:play-services-ads"])
        # La licence RÉELLE se lit (point 4) : ses deux bibliothèques.
        reelles = g.GOOGLE.autorisees(g.GOOGLE.LICENCE.read_text(encoding="utf-8"))
        self.assertEqual(reelles, permises)

    def test_la_declaration_du_depot_est_complete(self):
        """Chaque motif, licence et texte de la déclaration versionnée tient (hors Gradle)."""
        declaration = json.loads((RACINE / "app/src/main/tiers/composants.json").read_text(encoding="utf-8"))
        for c in declaration["composants"]:
            for t in c["textes"]:
                self.assertTrue((RACINE / t).is_file(), t)
            for licence in c["licences"]:
                self.assertTrue(set(g.textes_de_licence(licence)) & set(c["textes"]), c["nom"])
            # #320 : aucun joker sur une famille Google, chaque module se nomme.
            for motif in c.get("modules", []):
                self.assertFalse(motif.startswith(("com.google.android.gms:", "com.google.firebase:")) and "*" in motif,
                                 motif)


if __name__ == "__main__":
    unittest.main()
