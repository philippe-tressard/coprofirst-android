#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
"""Le contrat des écrans dit ce qui EST (#321) : `python3 -I scripts/test_composants.py`.

`docs/COMPOSANTS.md` et `docs/composants/*.md` sont l'API qu'un écran consomme (`CLAUDE.md`).
Un catalogue que rien ne confronte au code annonce du livré comme « à venir », décrit des
membres renommés, et oublie ce que les menus partagent. Trois affirmations se confrontent :

- chaque identifiant cité entre accents graves dans un tableau du catalogue existe dans le
  Kotlin suivi ou dans un générateur (`scripts/generer_*.py`, pour les tables générées) —
  sauf la colonne « Pendant iOS », qui nomme du Swift, et les champs servis (`snake_case`) ;
- aucune section « À venir » : le travail à faire vit dans les tickets (étiquette `commun`) ;
- une déclaration publique de premier niveau de la couche commune (`commun/`, `charte/`,
  `navigation/`, `session/`, `:partage`, `:partage-android`) qu'emploient au moins deux
  menus (`fonctions/<menu>`) est nommée au catalogue, ou déclarée dans `HORS_CATALOGUE`
  avec sa raison (une déclaration qui n'en a plus besoin y est refusée).
"""

from __future__ import annotations

import collections
import pathlib
import re
import subprocess
import unittest

RACINE = pathlib.Path(__file__).resolve().parent.parent
APP = "app/src/main/java/fr/coprofirst/app/"
COUCHE = re.compile(rf"^({APP}(commun|charte|navigation|session)/|partage(-android)?/src/main/).*\.kt$")
MOT = re.compile(r"[A-Za-z_]\w*")
DECLARATION = re.compile(
    r"^(?:@\w+(?:\([^)]*\))?\s+)*(?:(?:public|inline|data|sealed|enum|abstract|open|suspend|const|value|fun interface)\s+)*"
    r"(?:fun|val|var|class|object|interface|typealias)\s+(?:<[^>]*>\s*)?(?:[\w.]+\.)?([A-Za-z_]\w*)", re.M)

#: nom → raison : employé par deux menus, il n'est pas une API de la couche commune.
HORS_CATALOGUE: dict[str, str] = {}

#: mot cité dans un tableau qui n'est pas un identifiant du code → raison (sans objet : refusé).
HORS_CODE: dict[str, str] = {
    "debug": "l'ensemble de sources de l'API simulée (app/src/debug), pas un identifiant",
    "minLength": "le mot du contrat OpenAPI dont le seuil est généré (contrat/openapi.json)",
}


def suivis(racine: pathlib.Path) -> list[str]:
    sortie = subprocess.run(["git", "ls-files", "--cached"], cwd=racine, capture_output=True,
                            text=True, check=True).stdout.splitlines()
    if not sortie:
        raise AssertionError("INCONNU : git ne connaît aucun fichier")
    return sortie


def catalogue(fichiers: list[str]) -> list[str]:
    docs = ["docs/COMPOSANTS.md"] + sorted(f for f in fichiers if re.match(r"docs/composants/[^/]+\.md$", f))
    if "docs/COMPOSANTS.md" not in fichiers:
        raise AssertionError("INCONNU : docs/COMPOSANTS.md n'est pas suivi")
    return docs


def identifiants_du_tableau(texte: str) -> list[tuple[int, str]]:
    """(ligne, identifiant) de chaque cellule hors colonne iOS ; les fichiers, phrases et champs servis exclus."""
    trouves, ios = [], None
    for n, ligne in enumerate(texte.splitlines(), 1):
        if not ligne.startswith("|"):
            ios = None
            continue
        cellules = [c.strip() for c in ligne.strip().strip("|").split("|")]
        if all(re.fullmatch(r":?-+:?", c) for c in cellules):
            continue
        if ios is None and not any("`" in c for c in cellules):
            ios = next((i for i, c in enumerate(cellules) if "iOS" in c), -1)
            continue
        for i, cellule in enumerate(cellules):
            if i == ios:
                continue
            for jeton in re.findall(r"`([^`]+)`", cellule.replace("\\|", "")):
                corps = re.sub(r"\(.*?\)|\[.*?\]|\{.*?\}|<.*?>", "", jeton)
                if re.search(r"[/\s'«]|\.(kt|kts|py|md|json|svg|xml|txt)$", corps) or not corps:
                    continue
                for mot in MOT.findall(corps):
                    if mot == mot.lower() and "_" in mot:
                        continue
                    trouves.append((n, mot))
    return trouves


def connus(racine: pathlib.Path, fichiers: list[str]) -> set[str]:
    code = [f for f in fichiers if f.endswith(".kt") or re.match(r"scripts/generer_[a-z_]+\.py$", f)]
    return {m for f in code for m in MOT.findall((racine / f).read_text(encoding="utf-8"))}


def partages_absents(racine: pathlib.Path, fichiers: list[str], nommes: set[str], hors: dict[str, str]) -> list[str]:
    declares: dict[str, str] = {}
    for f in (f for f in fichiers if COUCHE.search(f)):
        for m in DECLARATION.finditer((racine / f).read_text(encoding="utf-8")):
            declares.setdefault(m.group(1), f)
    menus = collections.defaultdict(set)
    for f in (f for f in fichiers if f.startswith(APP + "fonctions/") and f.endswith(".kt")):
        menu = f[len(APP + "fonctions/"):].split("/")[0]
        for mot in set(MOT.findall((racine / f).read_text(encoding="utf-8"))) & declares.keys():
            menus[mot].add(menu)
    if len(declares) < 100:
        raise AssertionError(f"INCONNU : {len(declares)} déclarations lues dans la couche commune")
    fautes = [f"{nom} ({declares[nom]}) : employé par {len(menus[nom])} menus, absent du catalogue"
              for nom in sorted(declares) if len(menus[nom]) >= 2 and nom not in nommes and nom not in hors]
    fautes += [f"HORS_CATALOGUE : {nom} sans objet (nommé au catalogue, ou plus partagé)"
               for nom in sorted(hors) if nom in nommes or len(menus[nom]) < 2]
    return fautes


class Catalogue(unittest.TestCase):
    def setUp(self):
        self.fichiers = suivis(RACINE)
        self.docs = catalogue(self.fichiers)
        self.textes = {d: (RACINE / d).read_text(encoding="utf-8") for d in self.docs}

    def test_identifiants_existent(self):
        mots = connus(RACINE, self.fichiers)
        cites = [(d, n, m) for d, t in self.textes.items() for n, m in identifiants_du_tableau(t)]
        self.assertGreater(len(cites), 300, "INCONNU : trop peu d'identifiants lus dans les tableaux")
        morts = [f"{d}:{n} : `{m}` n'existe dans aucun Kotlin suivi ni générateur"
                 for d, n, m in cites if m not in mots and m not in HORS_CODE]
        morts += [f"HORS_CODE : {m} sans objet" for m in HORS_CODE if m in mots or m not in {c for _, _, c in cites}]
        self.assertEqual(morts, [])

    def test_aucune_section_a_venir(self):
        sections = [f"{d}:{n}" for d, t in self.textes.items()
                    for n, l in enumerate(t.splitlines(), 1) if re.match(r"#+ .*\bà venir\b", l, re.I)]
        self.assertEqual(sections, [], "le travail à faire vit dans les tickets (étiquette `commun`)")

    def test_partages_nommes(self):
        nommes = {m for t in self.textes.values() for m in MOT.findall(t)}
        self.assertEqual(partages_absents(RACINE, self.fichiers, nommes, HORS_CATALOGUE), [])

    def test_autotest_lecture_des_tableaux(self):
        tableau = ("| API | Pendant iOS | Usage |\n|---|---|---|\n"
                   "| `Modifier.cliquable(action)` | `ListeEtFiche` | `motifImage`, `pages_order`, `Boutons.kt` |\n")
        self.assertEqual([m for _, m in identifiants_du_tableau(tableau)], ["Modifier", "cliquable", "motifImage"])


if __name__ == "__main__":
    unittest.main()
