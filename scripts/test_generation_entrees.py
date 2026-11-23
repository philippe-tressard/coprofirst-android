#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
"""Une génération se relance quand le code qu'elle importe change (#330) : `python3 -I scripts/test_generation_entrees.py`.

Au poste, `build/` est gardé et le démon partagé entre copies de travail : une tâche dont une
entrée n'est pas déclarée reste « à jour » sur un Kotlin généré périmé, et les tests locaux
passent contre lui. `generation()` (`buildSrc/…/GenerationPython.kt`) déclare donc d'office
tout `scripts/*.py` hors tests (`voisins`). Ce test tient les trois bouts :

- `generation()` porte toujours cette entrée commune ;
- chaque module qu'un générateur charge (`import`, `from … import`, ou un chemin
  `spec_from_file_location` / `with_name`) est un `scripts/*.py` hors tests — donc couvert ;
- aucun build ne redéclare à la main un `scripts/*.py` en entrée : une seconde écriture
  qui finirait par diverger (elle en oubliait onze, #330).
"""

from __future__ import annotations

import ast
import pathlib
import re
import subprocess
import unittest

RACINE = pathlib.Path(__file__).resolve().parent.parent
GENERATION = "buildSrc/src/main/kotlin/GenerationPython.kt"
VOISINS = re.compile(
    r'voisins\.from\(rootProject\.fileTree\(racine\.dir\("scripts"\)\)\s*\{\s*include\("\*\.py"\);\s*exclude\("test_\*\.py"\)\s*\}\)')


def suivis() -> list[str]:
    sortie = subprocess.run(["git", "ls-files", "--cached"], cwd=RACINE, capture_output=True,
                            text=True, check=True).stdout.splitlines()
    if not sortie:
        raise AssertionError("INCONNU : git ne connaît aucun fichier")
    return sortie


def charges(source: str) -> set[str]:
    """Les noms de modules qu'un script charge : imports, et fichiers `.py` nommés par un littéral."""
    noms: set[str] = set()
    for noeud in ast.walk(ast.parse(source)):
        if isinstance(noeud, ast.Import):
            noms |= {a.name.split(".")[0] for a in noeud.names}
        elif isinstance(noeud, ast.ImportFrom) and noeud.module and noeud.level == 0:
            noms.add(noeud.module.split(".")[0])
        elif isinstance(noeud, ast.Constant) and isinstance(noeud.value, str) and re.fullmatch(r"\w+\.py", noeud.value):
            noms.add(noeud.value[:-3])
    return noms


def fautes_des_chargements(fichiers: list[str], sources: dict[str, str]) -> list[str]:
    scripts = {pathlib.PurePath(f).stem for f in fichiers if re.fullmatch(r"scripts/\w+\.py", f)}
    fautes = []
    for f, source in sorted(sources.items()):
        for nom in sorted(charges(source) & scripts):
            if nom.startswith("test_"):
                fautes.append(f"{f} charge {nom}.py, un test — hors des entrées de la génération")
    return fautes


def redeclarations(textes: dict[str, str]) -> list[str]:
    """Un `scripts/*.py` nommé en entrée par un build, hors du générateur d'une `generation(…)`."""
    fautes = []
    for f, texte in sorted(textes.items()):
        for n, ligne in enumerate(texte.splitlines(), 1):
            for m in re.finditer(r'"(scripts/\w+\.py)"', ligne):
                generateur = re.search(r'generation\(\s*"[^"]+",\s*[^,]+,\s*"' + re.escape(m.group(1)) + '"', ligne) \
                    or re.match(r'\s*"[^"]+",\s*[^,]+,\s*"' + re.escape(m.group(1)) + '"', ligne)
                if not generateur:
                    fautes.append(f"{f}:{n} : {m.group(1)} redéclaré en entrée — `generation()` l'a déjà (voisins, #330)")
    return fautes


class EntreesDeGeneration(unittest.TestCase):
    def setUp(self):
        self.fichiers = suivis()

    def test_generation_porte_les_voisins(self):
        texte = (RACINE / GENERATION).read_text(encoding="utf-8")
        self.assertRegex(texte, VOISINS, "generation() ne déclare plus scripts/*.py (hors tests) en entrée")

    def test_chaque_chargement_est_couvert(self):
        generateurs = [f for f in self.fichiers if re.fullmatch(r"scripts/generer_\w+\.py", f)]
        self.assertGreater(len(generateurs), 10, "INCONNU : trop peu de générateurs lus")
        sources = {f: (RACINE / f).read_text(encoding="utf-8") for f in generateurs}
        self.assertTrue(any("generer_tables" in charges(s) for s in sources.values()), "INCONNU : aucun import lu")
        self.assertEqual(fautes_des_chargements(self.fichiers, sources), [])

    def test_aucune_redeclaration(self):
        builds = [f for f in self.fichiers if f.endswith(".kts") or f.startswith("buildSrc/src/")]
        textes = {f: (RACINE / f).read_text(encoding="utf-8") for f in builds}
        self.assertEqual(redeclarations(textes), [])

    def test_autotest(self):
        self.assertEqual(charges("import generer_tables as gt\nfrom generer_charte import p\n"
                                 "x = pathlib.Path(__file__).with_name('tables_communes.py')\nimport json\n"),
                         {"generer_tables", "generer_charte", "tables_communes", "json"})
        self.assertEqual(len(fautes_des_chargements(["scripts/test_x.py"], {"scripts/generer_a.py": "import test_x\n"})), 1)
        fautif = {"a.kts": 'genererTables.configure { inputs.file(r.file("scripts/generer_charte.py")) }\n'
                           '    lus = listOf("contrat/t.json", "scripts/generer_tables.py"),\n'}
        propre = {"a.kts": 'val g = generation("genererLogo", "logo.svg", "scripts/generer_logo.py")\n'
                           '    "genererTraces", "contrat/icones-svg.json", "scripts/generer_traces.py",\n'}
        self.assertEqual(len(redeclarations(fautif)), 2)
        self.assertEqual(redeclarations(propre), [])


if __name__ == "__main__":
    unittest.main()
