#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
"""Les liens d'application du manifeste suivent leurs sources (#335) : `python3 -I scripts/test_liens_application.py`.

Le filtre des liens d'application (`app/src/main/AndroidManifest.xml`) ne peut pas lire une
table : il s'écrit à la main. Il se confronte donc ici à ce qu'il recopie —

- ses hôtes sont exactement `INSTALLATIONS` de `contrat/plancher.env`, les installations
  dont la concordance quotidienne mesure le contrat servi : un hôte ajouté d'un seul côté
  échapperait à la mesure, ou ne s'ouvrirait pas dans l'app ;
- la page des affaires qu'il ouvre (`/tickets` et `/tickets/…`) est la route de la page
  `mes-demandes` de la table GÉNÉRÉE (`contrat/tables-clients.json`), celle que lit
  `CheminDuSite.affaires`.

Une lecture vide (aucun hôte, aucune route) est un échec, jamais un succès.
"""

import json
import pathlib
import re
import unittest
import xml.etree.ElementTree as ET

RACINE = pathlib.Path(__file__).resolve().parent.parent
ANDROID = "{http://schemas.android.com/apk/res/android}"


def filtre_des_liens(manifeste: str) -> tuple[set[str], set[str]]:
    """Les hôtes et les chemins (`path`, `pathPrefix`) du filtre vérifié des liens d'application."""
    racine = ET.fromstring(manifeste)
    hotes, chemins = set(), set()
    for filtre in racine.iter("intent-filter"):
        if filtre.get(f"{ANDROID}autoVerify") != "true":
            continue
        for donnee in filtre.iter("data"):
            if donnee.get(f"{ANDROID}host"):
                hotes.add(donnee.get(f"{ANDROID}host"))
            for attribut in ("path", "pathPrefix"):
                if donnee.get(f"{ANDROID}{attribut}"):
                    chemins.add(donnee.get(f"{ANDROID}{attribut}"))
    if not hotes:
        raise AssertionError("INCONNU : aucun hôte lu dans le filtre des liens d'application")
    return hotes, chemins


def installations(plancher: str) -> set[str]:
    m = re.search(r'^INSTALLATIONS="([^"]*)"', plancher, flags=re.M)
    if not m or not m.group(1).split():
        raise AssertionError("INCONNU : INSTALLATIONS introuvable ou vide dans contrat/plancher.env")
    return set(m.group(1).split())


def route_des_affaires(tables: dict) -> str:
    pages = tables["tables"]["pages"]["valeur"]
    route = next((p.get("href") for p in pages if p.get("id") == "mes-demandes"), None)
    if not route:
        raise AssertionError("INCONNU : la page mes-demandes n'a pas de route dans tables-clients.json")
    return route


def ecarts(hotes: set[str], chemins: set[str], connues: set[str], affaires: str) -> list[str]:
    erreurs = []
    if hotes != connues:
        erreurs.append(f"hôtes du manifeste {sorted(hotes)} ≠ INSTALLATIONS {sorted(connues)}")
    for attendu in (affaires, affaires + "/"):
        if attendu not in chemins:
            erreurs.append(f"le manifeste n'ouvre pas « {attendu} » (route des affaires de la table)")
    return erreurs


class LiensApplication(unittest.TestCase):
    def test_le_manifeste_suit_ses_sources(self):
        hotes, chemins = filtre_des_liens((RACINE / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8"))
        connues = installations((RACINE / "contrat/plancher.env").read_text(encoding="utf-8"))
        tables = json.loads((RACINE / "contrat/tables-clients.json").read_text(encoding="utf-8"))
        self.assertEqual([], ecarts(hotes, chemins, connues, route_des_affaires(tables)))

    def test_refuse_un_hote_ou_une_route_d_un_seul_cote(self):
        manifeste = """<manifest xmlns:android="http://schemas.android.com/apk/res/android"><application><activity>
          <intent-filter android:autoVerify="true"><data android:host="a.test" /><data android:host="b.test" />
          <data android:path="/tickets" /><data android:pathPrefix="/tickets/" /></intent-filter>
        </activity></application></manifest>"""
        hotes, chemins = filtre_des_liens(manifeste)
        self.assertEqual([], ecarts(hotes, chemins, {"a.test", "b.test"}, "/tickets"))
        self.assertEqual(1, len(ecarts(hotes, chemins, {"a.test"}, "/tickets")))
        self.assertEqual(2, len(ecarts(hotes, chemins, {"a.test", "b.test"}, "/affaires")))

    def test_une_lecture_vide_n_est_pas_un_succes(self):
        with self.assertRaises(AssertionError):
            filtre_des_liens('<manifest xmlns:android="http://schemas.android.com/apk/res/android" />')
        with self.assertRaises(AssertionError):
            installations('INSTALLATIONS=""')
        with self.assertRaises(AssertionError):
            route_des_affaires({"tables": {"pages": {"valeur": [{"id": "fil", "href": "/"}]}}})


if __name__ == "__main__":
    unittest.main()
