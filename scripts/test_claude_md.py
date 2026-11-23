#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
"""`CLAUDE.md` dit ce qui EST (#316) : `python3 -I scripts/test_claude_md.py`.

Il se relit en tête de chaque session : une phrase que le code a quittée s'y applique
quand même. Deux affirmations se confrontent au dépôt :

- les dispenses de requête écrite à la main qu'il nomme sont exactement les fichiers
  que `DISPENSES_REQUETE` (`scripts/verifier-depot.sh`) dispense — la regex est
  appliquée telle quelle à `git ls-files`, jamais découpée ;
- un chemin du dépôt qu'il cite entre accents graves existe : un chemin qui commence
  par un dossier de la racine est suivi par git (fichier ou dossier) ; un nom de
  fichier de code (`.kt`, `.kts`, `.py`, `.sh`) est le nom d'un fichier suivi. Un motif
  (`<menu>`, `*`, `…`) n'est pas un chemin ; un chemin absolu ou d'un autre dépôt non plus.
"""

import pathlib
import re
import subprocess
import unittest

RACINE = pathlib.Path(__file__).resolve().parent.parent
CODE = (".kt", ".kts", ".py", ".sh")


def suivis() -> list[str]:
    sortie = subprocess.run(["git", "ls-files", "--cached"], cwd=RACINE, capture_output=True,
                            text=True, check=True).stdout.splitlines()
    if not sortie:
        raise AssertionError("INCONNU : git ne connaît aucun fichier")
    return sortie


def dispenses_du_script(script: str, fichiers: list[str]) -> set[str]:
    """Les fichiers suivis que `DISPENSES_REQUETE` dispense ; aucun = la regex n'a pas été lue."""
    m = re.search(r"^DISPENSES_REQUETE='([^']+)'", script, flags=re.M)
    if not m:
        raise AssertionError("DISPENSES_REQUETE introuvable dans verifier-depot.sh")
    motif = re.compile(m.group(1))
    trouves = {f for f in fichiers if motif.search(f)}
    if not trouves:
        raise AssertionError("DISPENSES_REQUETE ne dispense aucun fichier suivi : contrôle INCONNU")
    return trouves


def paragraphe_reseau(consignes: str) -> str:
    m = re.search(r"^- \*\*Réseau\*\*.*?(?=^- \*\*|\Z)", consignes, flags=re.M | re.S)
    if not m:
        raise AssertionError("CLAUDE.md : paragraphe « Réseau » introuvable")
    return m.group(0)


def dispenses_nommees(paragraphe: str, fichiers: list[str]) -> set[str]:
    """Les fichiers suivis dont le paragraphe cite le chemin (ou sa fin) entre accents graves."""
    cites = [c for c in re.findall(r"`([^`]+\.kt)`", paragraphe)]
    return {f for f in fichiers for c in cites if f == c or f.endswith("/" + c)}


def chemins_morts(consignes: str, fichiers: list[str]) -> list[str]:
    racines = {f.split("/", 1)[0] for f in fichiers if "/" in f}
    dossiers = {"/".join(f.split("/")[:i]) for f in fichiers for i in range(1, f.count("/") + 1)}
    morts = []
    for cite in re.findall(r"`([^`\s]+)`", consignes):
        if re.search(r"[<>*…{}$]", cite) or cite.startswith(("/", "~", "http")):
            continue
        chemin = cite.rstrip("/")
        if chemin.split("/", 1)[0] in racines:          # depuis la racine : exact
            existe = chemin in fichiers or chemin in dossiers
        elif chemin.endswith(CODE):                     # nom ou fin de chemin d'un fichier de code
            existe = any(f == chemin or f.endswith("/" + chemin) for f in fichiers)
        else:
            continue
        if not existe:
            morts.append(cite)
    return morts


class ClaudeMd(unittest.TestCase):
    def setUp(self):
        self.fichiers = suivis()
        self.consignes = (RACINE / "CLAUDE.md").read_text(encoding="utf-8")
        self.script = (RACINE / "scripts" / "verifier-depot.sh").read_text(encoding="utf-8")

    def test_les_dispenses_de_requete_nommees_sont_celles_du_script(self):
        self.assertEqual(dispenses_nommees(paragraphe_reseau(self.consignes), self.fichiers),
                         dispenses_du_script(self.script, self.fichiers))

    def test_chaque_chemin_cite_existe(self):
        self.assertEqual(chemins_morts(self.consignes, self.fichiers), [])

    # Cas zéro : le contrôle mord sur ce qu'il doit refuser.
    def test_une_dispense_oubliee_se_voit(self):
        fichiers = ["app/src/main/java/a/Une.kt", "app/src/main/java/b/Deux.kt"]
        script = "DISPENSES_REQUETE='^app/src/main/java/(a/Une|b/Deux)\\.kt$'\n"
        para = "- **Réseau** : sauf `a/Une.kt`.\n"
        self.assertNotEqual(dispenses_nommees(para, fichiers), dispenses_du_script(script, fichiers))

    def test_une_regex_qui_ne_dispense_rien_est_inconnue(self):
        with self.assertRaises(AssertionError):
            dispenses_du_script("DISPENSES_REQUETE='^rien$'\n", ["app/x.kt"])

    def test_un_chemin_mort_se_voit(self):
        fichiers = ["scripts/a.py", "app/build.gradle.kts", "app/src/main/java/commun/Boutons.kt"]
        texte = "`scripts/b.py` `Absent.kt` `scripts/` `app/build.gradle.kts` `commun/Boutons.kt`" \
                " `fonctions/<menu>` `Parite/PARITE.md` `/Volumes/x`"
        self.assertEqual(chemins_morts(texte, fichiers), ["scripts/b.py", "Absent.kt"])


if __name__ == "__main__":
    unittest.main()
