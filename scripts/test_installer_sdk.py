#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
"""Tests de `scripts/installer-sdk.sh` (#232) : `python3 -I scripts/test_installer_sdk.py`.

Un faux sdkmanager échoue un nombre réglé de fois, puis installe : le script réessaie,
dans la borne, et ne demande que les paquets manquants ; un paquet présent ne se
télécharge pas ; un sdkmanager qui sort à 0 sans rien installer (le zip tronqué) ne passe
pas pour un succès.
"""

import os
import pathlib
import subprocess
import tempfile
import unittest

RACINE = pathlib.Path(__file__).resolve().parent.parent
SCRIPT = RACINE / "scripts" / "installer-sdk.sh"
# Le faux sdkmanager : note ses arguments, échoue tant que le compteur n'est pas épuisé
# (code de sortie ÉCHEC), puis crée le package.xml de chaque paquet demandé.
FAUX = """#!/usr/bin/env bash
echo "$*" >> "$JOURNAL"
n=$(cat "$ECHECS")
if [ "$n" -gt 0 ]; then echo $((n - 1)) > "$ECHECS"; exit "$ECHEC"; fi
for p in "${@:2}"; do mkdir -p "$ANDROID_HOME/${p//;//}"; touch "$ANDROID_HOME/${p//;//}/package.xml"; done
"""
IMAGE = "system-images;android-35;google_apis;x86_64"


class InstallerSdk(unittest.TestCase):
    def setUp(self):
        self.dossier = pathlib.Path(tempfile.mkdtemp())
        self.sdk = self.dossier / "sdk"
        self.sdk.mkdir()
        faux = self.dossier / "sdkmanager"
        faux.write_text(FAUX, encoding="utf-8")
        faux.chmod(0o755)
        self.journal = self.dossier / "journal"
        self.journal.write_text("", encoding="utf-8")
        self.echecs = self.dossier / "echecs"
        self.env = dict(os.environ, ANDROID_HOME=str(self.sdk), SDKMANAGER=str(faux), ESSAIS="3",
                        ATTENTE_REESSAI="0", JOURNAL=str(self.journal), ECHECS=str(self.echecs), ECHEC="1")

    def lancer(self, *paquets, echecs=0, **env):
        self.echecs.write_text(str(echecs), encoding="utf-8")
        return subprocess.run(["bash", str(SCRIPT), *paquets], env=dict(self.env, **env),
                              capture_output=True, text=True)

    def appels(self):
        return self.journal.read_text(encoding="utf-8").splitlines()

    def test_deux_echecs_puis_l_installation_passe_dans_la_borne(self):
        r = self.lancer(IMAGE, "emulator", echecs=2)
        self.assertEqual(r.returncode, 0, r.stderr)
        self.assertEqual(len(self.appels()), 3)
        self.assertTrue((self.sdk / "system-images/android-35/google_apis/x86_64/package.xml").is_file())

    def test_au_dela_de_la_borne_le_script_echoue(self):
        r = self.lancer(IMAGE, echecs=5)
        self.assertEqual(r.returncode, 1)
        self.assertEqual(len(self.appels()), 3)
        self.assertIn("toujours absent après 3 essais", r.stderr)

    def test_une_sortie_a_zero_sans_paquet_n_est_pas_un_succes(self):
        r = self.lancer(IMAGE, echecs=5, ECHEC="0")
        self.assertEqual(r.returncode, 1)

    def test_un_paquet_present_ne_se_telecharge_pas(self):
        (self.sdk / "emulator").mkdir()
        (self.sdk / "emulator" / "package.xml").touch()
        r = self.lancer(IMAGE, "emulator")
        self.assertEqual(r.returncode, 0, r.stderr)
        self.assertEqual(self.appels(), [f"--install {IMAGE}"])
        r = self.lancer(IMAGE, "emulator")
        self.assertEqual(r.returncode, 0, r.stderr)
        self.assertEqual(len(self.appels()), 1)

    def test_sans_paquet_le_script_refuse(self):
        self.assertEqual(self.lancer().returncode, 2)


if __name__ == "__main__":
    unittest.main()
