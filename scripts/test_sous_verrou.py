#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
"""Tests de `scripts/sous-verrou.sh` (#55) : `python3 -I scripts/test_sous_verrou.py`.

Deux tâches lourdes lancées en même temps sous le verrou commun du poste
(`~/.claude/bin/verrou_poste.py`, claude-config#552) passent l'une après l'autre ; sans le
verrou (la CI), la commande tourne telle quelle ; son code de sortie est rendu.
"""

import os
import pathlib
import subprocess
import sys
import tempfile
import unittest

RACINE = pathlib.Path(__file__).resolve().parent.parent
ENVELOPPE = RACINE / "scripts" / "sous-verrou.sh"
VERROU = pathlib.Path.home() / ".claude" / "bin" / "verrou_poste.py"
# Une tâche qui note son début et sa fin dans un journal, avec une seconde entre les deux.
TACHE = ("import sys,time;j=open(sys.argv[1],'a');j.write(f'debut {time.time()}\\n');j.flush();"
         "time.sleep(1);j.write(f'fin {time.time()}\\n')")


@unittest.skipUnless(VERROU.is_file(), "le verrou commun du poste est absent (la CI) : rien à prouver ici")
class SousVerrou(unittest.TestCase):
    def setUp(self):
        self.dossier = tempfile.mkdtemp()
        self.env = dict(os.environ, VERROU_POSTE_DIR=self.dossier, VERROU_POSTE_ATTENTE="0.05")
        self.env.pop("VERROU_POSTE_TENU", None)

    def lancer(self, journal, env=None):
        return subprocess.Popen(
            ["bash", str(ENVELOPPE), "essai", "--", sys.executable, "-c", TACHE, journal],
            env=env or self.env, cwd=RACINE)

    def test_deux_lancements_simultanes_passent_l_un_apres_l_autre(self):
        journal = os.path.join(self.dossier, "journal")
        a, b = self.lancer(journal), self.lancer(journal)
        self.assertEqual((a.wait(30), b.wait(30)), (0, 0))
        lignes = [l.split() for l in pathlib.Path(journal).read_text().splitlines()]
        self.assertEqual([m for m, _ in lignes], ["debut", "fin", "debut", "fin"], "aucun chevauchement")
        self.assertLessEqual(float(lignes[1][1]), float(lignes[2][1]), "la seconde attend la fin de la première")

    def test_le_code_de_la_commande_est_rendu(self):
        r = subprocess.run(["bash", str(ENVELOPPE), "essai", "--", "bash", "-c", "exit 7"], env=self.env, cwd=RACINE)
        self.assertEqual(r.returncode, 7)


class SansVerrou(unittest.TestCase):
    def test_sans_le_verrou_la_commande_tourne_telle_quelle(self):
        env = dict(os.environ, VERROU_POSTE="/introuvable/verrou_poste.py")
        env.pop("VERROU_POSTE_TENU", None)
        r = subprocess.run(["bash", str(ENVELOPPE), "essai", "--", "bash", "-c", "echo seule; exit 3"],
                           env=env, cwd=RACINE, capture_output=True, text=True)
        self.assertEqual((r.returncode, r.stdout.strip()), (3, "seule"))


class TachesLourdes(unittest.TestCase):
    """Les scripts du dépôt qui lancent Gradle ou l'émulateur passent par l'enveloppe."""

    def test_chaque_tache_lourde_prend_le_verrou(self):
        for script in ("tests-interface.sh", "construire-release.sh"):
            texte = (RACINE / "scripts" / script).read_text(encoding="utf-8")
            self.assertIn(f"exec bash scripts/sous-verrou.sh", texte, script)
            self.assertIn(f"-- bash scripts/{script}", texte, f"{script} se relance lui-même sous le verrou")


if __name__ == "__main__":
    unittest.main()
