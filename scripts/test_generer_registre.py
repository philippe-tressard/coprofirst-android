#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
"""Tests du générateur du registre des menus : `python3 -I scripts/test_generer_registre.py`."""

import pathlib
import sys
import tempfile
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).parent))
import generer_registre as g  # noqa: E402

RACINE = pathlib.Path(__file__).resolve().parent.parent


def ecrire(dossier: pathlib.Path, menu: str, texte: str) -> None:
    (dossier / menu).mkdir(parents=True, exist_ok=True)
    (dossier / menu / "Inscription.kt").write_text(texte, encoding="utf-8")


class Registre(unittest.TestCase):
    def test_une_inscription_par_paquet_dans_l_ordre(self):
        with tempfile.TemporaryDirectory() as d:
            racine = pathlib.Path(d)
            ecrire(racine, "fil", "package fr.coprofirst.app.fonctions.fil\n\nobject InscriptionFil : InscriptionDeMenu {\n}\n")
            ecrire(racine, "faq", "package fr.coprofirst.app.fonctions.faq\n\ninternal object InscriptionFaq : InscriptionDeMenu\n")
            texte = g.kotlin(racine)
            self.assertLess(texte.index("faq.InscriptionFaq,"), texte.index("fil.InscriptionFil,"))

    def test_une_inscription_illisible_arrete(self):
        cas = [
            "object InscriptionX : InscriptionDeMenu\n",  # sans paquet
            "package fr.coprofirst.app.fonctions.autre\nobject InscriptionX : InscriptionDeMenu\n",  # mauvais paquet
            "package fr.coprofirst.app.fonctions.x\nobject Rien\n",  # aucun objet
            "package fr.coprofirst.app.fonctions.x\nobject A : InscriptionDeMenu\nobject B : InscriptionDeMenu\n",
        ]
        for texte in cas:
            with self.subTest(texte=texte), tempfile.TemporaryDirectory() as d:
                ecrire(pathlib.Path(d), "x", texte)
                with self.assertRaises(g.InscriptionIllisible):
                    g.kotlin(pathlib.Path(d))

    def test_le_depot_se_genere(self):
        # Les inscriptions versionnées se lisent toutes.
        texte = g.kotlin(RACINE / "app/src/main/java/fr/coprofirst/app/fonctions")
        self.assertIn("fr.coprofirst.app.fonctions.fil.InscriptionFil,", texte)


if __name__ == "__main__":
    unittest.main()
