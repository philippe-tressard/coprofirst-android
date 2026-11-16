#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
"""Tests de `scripts/generer_balises.py` (#206, lot 2c) : `python3 -I scripts/test_generer_balises.py`.

La liste blanche du texte riche se génère dans `:partage-android`, et UNE fois : depuis la
table publiée au plancher, de la forme de `generer_tables.kotlin_balises` — et
`generer_tables.py`, qui génère pour `:app`, ne l'écrit plus (deux `BalisesDuSite`).
"""

import json
import pathlib
import subprocess
import sys
import tempfile
import unittest

RACINE = pathlib.Path(__file__).resolve().parent.parent
TABLES = RACINE / "contrat" / "tables-clients.json"
sys.path.insert(0, str(RACINE / "scripts"))
import generer_tables as gt  # noqa: E402


class GenererBalises(unittest.TestCase):
    def generer(self, script: str) -> pathlib.Path:
        sortie = pathlib.Path(tempfile.mkdtemp())
        r = subprocess.run([sys.executable, "-I", str(RACINE / "scripts" / script), str(TABLES), str(sortie)],
                           capture_output=True, text=True)
        self.assertEqual(r.returncode, 0, r.stderr)
        return sortie

    def test_la_liste_blanche_se_genere_depuis_la_table_publiee(self):
        fichier = self.generer("generer_balises.py") / "fr/coprofirst/app/commun/texte/BalisesDuSite.kt"
        tables = json.loads(TABLES.read_text(encoding="utf-8"))["tables"]
        self.assertEqual(fichier.read_text(encoding="utf-8"), gt.kotlin_balises(tables, TABLES.name))

    def test_generer_tables_ne_l_ecrit_plus(self):
        self.assertEqual(list(self.generer("generer_tables.py").rglob("BalisesDuSite.kt")), [])


if __name__ == "__main__":
    unittest.main()
