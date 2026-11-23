#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
"""Tests du contrôle de la section « Sécurité des données » :
`python3 -I scripts/test_verifier_securite_donnees.py`."""

import json
import pathlib
import sys
import tempfile
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).parent))
import verifier_securite_donnees as v  # noqa: E402

RACINE = pathlib.Path(__file__).resolve().parent.parent

DECLARATION = """
## 3. Les données
| Code | Case |
|---|---|
| `Nom` | Nom |
## 4. Envois
<!-- table:envois -->
| Envoi | Données |
|---|---|
| `postAuthLogin` | `Nom` |
| `postAuthLogout` | — |
| `postAppareils` | `Nom` — à venir (#23) |

<!-- table:permissions -->
| Permission | Usage |
|---|---|
| `android.permission.INTERNET` | réseau |
| `android.permission.CAMERA` | QR — à venir (#6) |
"""
ECRITURES = {"postAuthLogin", "postAuthLogout", "postAppareils", "postTickets"}
SOURCE = "auth { postAuthLogin(corps) }\nAuthApi(b, c).postAuthLogout()"
MANIFESTE = '<uses-permission android:name="android.permission.INTERNET" />'


class SecuriteDesDonnees(unittest.TestCase):
    def test_nom_genere(self):
        self.assertEqual(v.nom_kotlin("post_auth_change-password"), "postAuthChangePassword")

    def test_conforme(self):
        self.assertEqual(v.verifier(DECLARATION, ECRITURES, SOURCE, MANIFESTE), [])

    def test_ce_qui_ne_dit_pas_la_verite_echoue(self):
        cas = {
            "envoi neuf non classé": (DECLARATION, SOURCE + "\npostTickets(x)", MANIFESTE),
            "ligne sans appel": (DECLARATION.replace("— à venir (#23)", ""), SOURCE, MANIFESTE),
            "donnée hors du §3": (DECLARATION.replace("| `postAuthLogin` | `Nom` |", "| `postAuthLogin` | `Adresse` |"),
                                  SOURCE, MANIFESTE),
            "permission neuve": (DECLARATION, SOURCE, MANIFESTE + '<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"/>'),
            "permission retirée": (DECLARATION, SOURCE, ""),
            "permission à venir démarquée": (DECLARATION.replace("QR — à venir (#6)", "QR"), SOURCE, MANIFESTE),
            "envoi à venir déjà là": (DECLARATION, SOURCE + "\npostAppareils(x)", MANIFESTE),
            "permission à venir déjà là": (DECLARATION, SOURCE, MANIFESTE + '<uses-permission android:name="android.permission.CAMERA"/>'),
            "pas une écriture du contrat": (DECLARATION.replace("`postAuthLogout`", "`postInventee`"), SOURCE, MANIFESTE),
        }
        for nom, (texte, source, manifeste) in cas.items():
            self.assertTrue(v.verifier(texte, ECRITURES, source, manifeste), nom)

    def test_un_tableau_absent_rend_inconnu(self):
        with self.assertRaises(v.Inconnu):
            v.verifier(DECLARATION.replace("<!-- table:envois -->", ""), ECRITURES, SOURCE, MANIFESTE)

    def test_les_ecritures_greffees_au_dessus_du_plancher_se_declarent(self):
        # #282 : le contrôle ne lisait que le contrat du plancher ; une écriture greffée passait
        # sans ligne. Aucune greffe depuis #311 : un dépôt fictif éprouve la lecture.
        with tempfile.TemporaryDirectory() as dossier:
            racine = pathlib.Path(dossier)
            (racine / "contrat/au-dessus").mkdir(parents=True)
            (racine / "contrat/plancher.env").write_text('AU_DESSUS=v9.9.9\nGREFFES="/masquages"\n', encoding="utf-8")
            haut = {"paths": {"/masquages": {"get": {"operationId": "get_masquages"}, "post": {"operationId": "post_masquages"}},
                              "/autre": {"post": {"operationId": "post_autre"}}}}
            (racine / "contrat/au-dessus/openapi.json").write_text(json.dumps(haut), encoding="utf-8")
            self.assertEqual(v.ecritures_du_contrat(v.greffees(racine)), {"postMasquages"})

    def test_les_masquages_sont_au_plancher(self):
        # #311 : /masquages est au plancher (v3.121.2) ; ses écritures se déclarent comme les autres.
        contrat = json.loads((RACINE / "contrat/openapi.json").read_text(encoding="utf-8"))
        ecritures = v.ecritures_du_contrat(contrat)
        self.assertIn("postMasquages", ecritures)
        self.assertIn("deleteMasquagesAuteurId", ecritures)
        self.assertEqual(v.greffees(RACINE), {"paths": {}})

    def test_le_depot_est_conforme(self):
        self.assertEqual(v.main(["x", str(RACINE)]), 0)


if __name__ == "__main__":
    unittest.main()
