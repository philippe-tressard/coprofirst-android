#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
"""Une section citée par son titre existe (#331) : `python3 -I scripts/test_renvois_sections.py`.

Le rapport d'environnement renvoyait à « README, « Outillage » », une section que le README
n'a jamais eue : qui suivait le renvoi ne trouvait rien. Ce test lit, dans chaque fichier
suivi (scripts, consignes, documents), un document Markdown nommé puis un titre entre
guillemets — `README, « Construire »`, `` `docs/play-store/guide-philippe.md` (« Avant CHAQUE
envoi ») `` — et exige qu'un repère de ce document contienne ce texte : un titre (`#`…), le
libellé d'une puce (`- **Version**`) ou la première cellule d'une ligne de tableau.
"""

from __future__ import annotations

import pathlib
import re
import subprocess
import unittest

RACINE = pathlib.Path(__file__).resolve().parent.parent
TEXTES = re.compile(r"\.(md|sh|py|kt|kts|yml|xml)$")
#: `README` ou un chemin `….md`, puis, à quelques signes près (virgule, accent grave, parenthèse), « titre ».
RENVOI = re.compile(r"(?<![\w/.-])((?:[\w./-]+/)?[\w.-]+\.md|README)`?[ ,(]{1,3}« ([^»\n]+?) »")


def renvois(texte: str) -> list[tuple[str, str]]:
    return [(m.group(1), m.group(2).strip()) for m in RENVOI.finditer(texte)]


def manquants(fichiers: list[str], lire) -> tuple[list[str], int]:
    fautes, vus = [], 0
    documents = {f for f in fichiers if f.endswith(".md")}
    for f in fichiers:
        if not TEXTES.search(f) or f == "scripts/test_renvois_sections.py":
            continue
        for doc, titre in renvois(lire(f)):
            chemin = "README.md" if doc in ("README", "README.md") else doc
            cible = chemin if chemin in documents else next((d for d in documents if d.endswith("/" + chemin)), None)
            if cible is None:
                continue          # un document d'un autre dépôt : pas le nôtre à juger
            vus += 1
            titres = [l.split("|")[1] if l.startswith("|") else l for l in lire(cible).splitlines()
                      if l.startswith(("#", "- **", "| "))]
            if not any(titre in t for t in titres):
                fautes.append(f"{f} : « {titre} » n'est pas un titre de {cible}")
    return fautes, vus


class RenvoisDeSections(unittest.TestCase):
    def test_le_depot(self):
        fichiers = subprocess.run(["git", "ls-files", "--cached"], cwd=RACINE, capture_output=True,
                                  text=True, check=True).stdout.splitlines()
        fautes, vus = manquants(fichiers, lambda f: (RACINE / f).read_text(encoding="utf-8"))
        self.assertGreater(vus, 2, "INCONNU : trop peu de renvois lus")
        self.assertEqual(fautes, [])

    def test_autotest(self):
        lu = {"README.md": "# CoproFirst\n## Construire\n## Licence\nOutillage, en passant.\n| a | Outillage |\n",
              "docs/guide.md": "## Avant CHAQUE envoi d'un AAB\n",
              "a.sh": 'echo "SDK : absent — README, « Outillage »"\necho "README, « Construire »"\n',
              "b.md": "voir `docs/guide.md` (« Avant CHAQUE envoi »), et `autre/depot.md` (« Rien »)\n"}
        fautes, vus = manquants(list(lu), lu.__getitem__)
        self.assertEqual(vus, 3)
        self.assertEqual(fautes, ["a.sh : « Outillage » n'est pas un titre de README.md"])


if __name__ == "__main__":
    unittest.main()
