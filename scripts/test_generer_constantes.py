#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
"""Tests du générateur des constantes du serveur :
`python3 -I scripts/test_generer_constantes.py`."""

import json
import pathlib
import sys
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).parent))
import generer_constantes as g  # noqa: E402
from generer_tables import TableInattendue  # noqa: E402

RACINE = pathlib.Path(__file__).resolve().parent.parent


def c(v):
    return {"source": "test", "quoi": "test", "valeur": v}


def constantes(**remplace):
    base = {
        "schemasDeLien": c(["http", "https", "mailto"]),
        "longueurMinMotDePasse": c(8),
        "noteMin": c(1), "noteMax": c(5), "noteRemarque": c(3), "notePubliable": c(4),
        "defautsPreferences": c({"mon_batiment_mail": True, "autres_batiments_mail": False}),
    }
    base.update(remplace)
    return base


class Constantes(unittest.TestCase):
    def test_toutes_les_constantes(self):
        texte = g.kotlin(constantes(), "test.json")
        self.assertIn('val schemasDeLien: Set<String> = setOf("http", "https", "mailto")', texte)
        self.assertIn("const val LONGUEUR_MIN_MOT_DE_PASSE = 8", texte)
        self.assertIn("const val NOTE_PUBLIABLE = 4", texte)
        self.assertIn('"autres_batiments_mail" to false,', texte)

    def test_ce_qui_change_de_forme_arrete(self):
        for casse in ({"schemasDeLien": c([])}, {"schemasDeLien": c(["javascript:"])}, {"longueurMinMotDePasse": c("8")},
                      {"longueurMinMotDePasse": c(True)}, {"noteRemarque": c(6)}, {"noteMax": None},
                      {"defautsPreferences": c({"x": 1})}):
            with self.assertRaises(TableInattendue, msg=str(casse)):
                g.kotlin({k: v for k, v in constantes(**casse).items() if v is not None}, "t")

    def test_le_plancher_se_genere(self):
        publiees = json.loads((RACINE / "contrat" / "constantes-clients.json").read_text(encoding="utf-8"))["constantes"]
        texte = g.kotlin(publiees, "constantes-clients.json")
        for attendu in ('"https"', "LONGUEUR_MIN_MOT_DE_PASSE = ", "NOTE_MIN = ", '"mon_batiment_mail" to '):
            self.assertIn(attendu, texte)

    def test_les_copies_ne_reviennent_pas(self):
        """#74 : ce que constantes-clients.json publie ne se recopie plus dans un générateur."""
        for script in (RACINE / "scripts").glob("generer_*.py"):
            source = script.read_text(encoding="utf-8")
            # #169 : les règles de fichiers publiées (v3.105.0, v3.106.0) ne se tiennent plus.
            for copie in ("SCHEMAS_DE_LIEN =", "SEUILS_AVIS_TENUS", "FICHIERS_EXTENSIONS_IMAGE", "FICHIERS_LONGUEUR_PREFIXE",
                          "EXTENSIONS_IMAGE =", "PREFIXE_UUID =", "FAMILLES_TENUES"):
                self.assertNotIn(copie, source, script.name)


class Fichiers(unittest.TestCase):
    """`FichiersDuSite` (#169) : les motifs publiés depuis v3.105.0, les familles d'affichage depuis v3.106.0."""

    FAMILLES = [("JPEG", "JPG"), ("XLSX", "EXCEL")]

    def motifs(self, **remplace):
        base = {"motifImage": c({"motif": r"\.(jpe?g|png)$", "ignorerCasse": True}),
                "prefixeStockage": c({"motif": "^[0-9a-f]{32}_", "ignorerCasse": False})}
        base.update(remplace)
        return base

    def test_les_motifs_publies_deviennent_des_regex(self):
        texte = g.kotlin_fichiers(self.motifs(), "t", self.FAMILLES)
        self.assertIn('val motifImage: Regex = Regex("\\\\.(jpe?g|png)\\$", RegexOption.IGNORE_CASE)', texte)
        self.assertIn('val prefixeStockage: Regex = Regex("^[0-9a-f]{32}_")', texte)
        self.assertIn('"XLSX" to "EXCEL",', texte)

    def test_le_plancher_se_genere(self):
        publiees = json.loads((RACINE / "contrat" / "constantes-clients.json").read_text(encoding="utf-8"))["constantes"]
        tables = json.loads((RACINE / "contrat" / "tables-clients.json").read_text(encoding="utf-8"))["tables"]
        texte = g.kotlin_fichiers(publiees, "constantes-clients.json", g.familles_affichage(tables))
        for cle, valeur in (("JPEG", "JPG"), ("XLS", "EXCEL"), ("DOCX", "WORD"), ("PPT", "POWERPOINT"), ("TXT", "TEXTE")):
            self.assertIn(f'"{cle}" to "{valeur}",', texte)
        self.assertIn("tiff?", texte)
        self.assertIn("{32}_", texte)

    def test_une_forme_inattendue_arrete(self):
        for casse in ({"motifImage": None}, {"motifImage": c({"motif": "(", "ignorerCasse": True})},
                      {"motifImage": c({"motif": "x", "ignorerCasse": "oui"})}, {"motifImage": c({"motif": ""})},
                      {"prefixeStockage": c({"motif": "(?P<h>[0-9a-f]{32})_", "ignorerCasse": True})},
                      {"prefixeStockage": c({"motif": "_\\Z", "ignorerCasse": True})}):
            with self.assertRaises(TableInattendue, msg=str(casse)):
                g.kotlin_fichiers({k: v for k, v in self.motifs(**casse).items() if v is not None}, "t", self.FAMILLES)
        for mauvais in (None, ["JPG"], {}):
            with self.assertRaises(TableInattendue, msg=repr(mauvais)):
                g.familles_affichage({"famillesAffichageFichiers": {"valeur": mauvais}})
        for familles in ((), (("docx", "WORD"),), (("DOC", "word"),), (("DOC", "WORD"), ("DOC", "TEXTE")), (("DOC", " "),)):
            with self.assertRaises(TableInattendue, msg=repr(familles)):
                g.kotlin_fichiers(self.motifs(), "t", familles)


if __name__ == "__main__":
    unittest.main()
