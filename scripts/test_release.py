#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
"""Le contrôle avant CHAQUE envoi est dans le script de release, pas seulement dans le guide (#322).

`python3 -I scripts/test_release.py`. `construire-release.sh` construit les AAB, rejuge le
manifeste fusionné (#327), puis lance lui-même le contrôle avant soumission avec `--taguer`
(#4) ; sa dernière porte, `pret-a-envoyer.sh`, retire les AAB si le commit ne porte pas
l'étiquette `v<version>` que seul un contrôle vert pose. Ce test :

- joue `pret-a-envoyer.sh` dans un dépôt jetable : sans l'étiquette, refus et AAB retirés ;
  avec, AAB gardés ;
- lit l'ordre de `construire-release.sh` : le build, puis le contrôle `--taguer`, puis la porte.
"""

from __future__ import annotations

import os
import pathlib
import shutil
import subprocess
import tempfile
import unittest

RACINE = pathlib.Path(__file__).resolve().parent.parent
PORTE = RACINE / "scripts" / "pret-a-envoyer.sh"
RELEASE = RACINE / "scripts" / "construire-release.sh"
# Joué par le pre-commit, git exporte GIT_DIR, GIT_INDEX_FILE… : le dépôt jetable ne doit pas les hériter.
SANS_GIT = {k: v for k, v in os.environ.items() if not k.startswith("GIT_")}


def git(dossier: pathlib.Path, *args: str) -> None:
    subprocess.run(["git", "-c", "user.name=essai", "-c", "user.email=essai@example.invalid", *args],
                   cwd=dossier, check=True, capture_output=True, env=SANS_GIT)


class PorteDEnvoi(unittest.TestCase):
    def setUp(self):
        self.dossier = pathlib.Path(tempfile.mkdtemp())
        git(self.dossier, "init", "-q")
        git(self.dossier, "commit", "-q", "--allow-empty", "-m", "essai")
        self.aab = self.dossier / "app-release.aab"
        self.aab.write_bytes(b"aab")

    def tearDown(self):
        shutil.rmtree(self.dossier)

    def porte(self, controle: str = "0") -> subprocess.CompletedProcess:
        return subprocess.run(["bash", str(PORTE), controle, "0.8.9", str(self.aab)], cwd=self.dossier,
                              capture_output=True, text=True, env=SANS_GIT)

    def test_vert_sans_etiquette_les_aab_sont_retires(self):
        self.assertEqual(self.porte().returncode, 1)
        self.assertFalse(self.aab.exists())

    def test_vert_avec_une_autre_etiquette_ne_suffit_pas(self):
        git(self.dossier, "tag", "v0.8.8")
        self.assertEqual(self.porte().returncode, 1)
        self.assertFalse(self.aab.exists())

    def test_rouge_avec_l_etiquette_les_aab_sont_retires(self):
        # Une relance sur un commit déjà étiqueté : « existe déjà », le contrôle sort rouge.
        git(self.dossier, "tag", "-a", "v0.8.9", "-m", "soumise")
        self.assertEqual(self.porte("1").returncode, 1)
        self.assertFalse(self.aab.exists())

    def test_inconnu_les_aab_sont_retires(self):
        git(self.dossier, "tag", "-a", "v0.8.9", "-m", "soumise")
        self.assertEqual(self.porte("2").returncode, 2)
        self.assertFalse(self.aab.exists())

    def test_vert_avec_l_etiquette_les_aab_restent(self):
        git(self.dossier, "tag", "-a", "v0.8.9", "-m", "soumise")
        self.assertEqual(self.porte("0").returncode, 0)
        self.assertTrue(self.aab.exists())


class OrdreDeLaRelease(unittest.TestCase):
    def test_build_puis_controle_puis_porte(self):
        texte = RELEASE.read_text(encoding="utf-8")
        reperes = [":app:bundleRelease", "python3 -I scripts/controle_avant_soumission.py --taguer",
                   "bash scripts/pret-a-envoyer.sh"]
        positions = [texte.find(r) for r in reperes]
        self.assertNotIn(-1, positions, f"construire-release.sh ne porte pas {reperes}")
        self.assertEqual(positions, sorted(positions), "l'ordre doit être : build, contrôle --taguer, porte")


if __name__ == "__main__":
    unittest.main()
