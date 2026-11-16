#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
"""Les composants tiers que l'app EMBARQUE, pour « À propos » (#27) — comme l'iOS
(`ComposantsTiers`, tenu par `scripts/verifier-dependances.py`).

La déclaration (`app/src/main/tiers/composants.json`) nomme chaque composant, ses
licences, son rôle, les modules Maven qu'il couvre (motifs `groupe:module`, `*` admis)
et les textes qui doivent accompagner chaque copie : l'avis de ses auteurs, puis la
licence entière. Gradle donne la liste des modules de la variante publiée
(`releaseRuntimeClasspath`) ; ce script la confronte à la déclaration, et s'ARRÊTE :

- sur un module embarqué qu'aucun composant ne couvre, ou que deux couvrent ;
- sur un motif qui ne couvre plus rien (un composant retiré reste déclaré) ;
- sur une licence sans son texte, ou un texte introuvable. Le texte d'une licence est
  `LICENSES/<id>.txt` quand un fichier du dépôt l'emploie, sinon
  `app/src/main/tiers/LICENSE-<id>.txt` : REUSE refuse dans `LICENSES/` une licence
  qu'aucun fichier du dépôt n'emploie (la Public Suffix List n'est que dans l'APK).

Une nomenclature (`*-bom`) n'embarque aucun code : elle est écartée.

Il écrit dans `<sortie>/tiers/` la déclaration, enrichie des modules couverts, et une
copie de chaque texte — ce que l'écran lit dans les ressources de l'app.

Usage : generer_composants_tiers.py <composants.json> <dossier de sortie> <modules.txt>
"""

from __future__ import annotations

import fnmatch
import json
import pathlib
import shutil
import sys

RACINE = pathlib.Path(__file__).resolve().parent.parent
DOSSIER = "tiers"


def textes_de_licence(licence: str) -> tuple[str, str]:
    """Où peut se trouver le texte d'une licence : dans `LICENSES/`, ou à côté de la déclaration."""
    return f"LICENSES/{licence}.txt", f"app/src/main/tiers/LICENSE-{licence}.txt"


class DeclarationInvalide(Exception):
    pass


def embarques(lignes: list[str]) -> list[str]:
    """Les modules `groupe:module` embarqués, sans les nomenclatures."""
    modules = sorted({l.strip() for l in lignes if l.strip()})
    return [m for m in modules if not m.split(":")[-1].endswith("-bom")]


def verifier(declaration: dict, modules: list[str], existe) -> list[dict]:
    """Les composants, chacun avec ses modules ; `existe(chemin)` dit si un texte est là."""
    composants = declaration.get("composants")
    if not isinstance(composants, list) or not composants:
        raise DeclarationInvalide("aucun composant déclaré")
    erreurs: list[str] = []
    couverts: dict[str, list[str]] = {}
    sortie = []
    for c in composants:
        nom = c.get("nom")
        motifs, licences, textes = c.get("modules") or [], c.get("licences") or [], c.get("textes") or []
        if not nom or not c.get("role") or not motifs or not licences or not textes:
            raise DeclarationInvalide(f"composant incomplet : {c}")
        siens = []
        for motif in motifs:
            trouves = [m for m in modules if fnmatch.fnmatchcase(m, motif)]
            if not trouves:
                erreurs.append(f"{nom} : le motif « {motif} » ne couvre plus aucun module embarqué")
            siens += trouves
        for m in siens:
            couverts.setdefault(m, []).append(nom)
        for licence in licences:
            if not set(textes_de_licence(licence)) & set(textes):
                erreurs.append(f"{nom} : la licence {licence} sans son texte ({' ou '.join(textes_de_licence(licence))})")
        erreurs += [f"{nom} : texte introuvable, {t}" for t in textes if not existe(t)]
        sortie.append({"nom": nom, "licences": licences, "role": c["role"], "modules": sorted(set(siens)),
                       "textes": [pathlib.PurePosixPath(t).name for t in textes]})
    erreurs += [f"module embarqué sans composant déclaré : {m}" for m in modules if m not in couverts]
    erreurs += [f"{m} couvert par deux composants : {', '.join(n)}" for m, n in couverts.items() if len(n) > 1]
    if erreurs:
        raise DeclarationInvalide("\n  ".join(["la déclaration ne dit pas ce que l'app embarque :"] + erreurs))
    return sortie


def main(argv: list[str]) -> int:
    if len(argv) != 4:
        print(__doc__.strip().splitlines()[-1], file=sys.stderr)
        return 2
    entree, sortie, liste = pathlib.Path(argv[1]), pathlib.Path(argv[2]) / DOSSIER, pathlib.Path(argv[3])
    declaration = json.loads(entree.read_text(encoding="utf-8"))
    try:
        composants = verifier(declaration, embarques(liste.read_text(encoding="utf-8").splitlines()),
                              lambda t: (RACINE / t).is_file())
    except DeclarationInvalide as e:
        print(f"❌ {entree.name} : {e}", file=sys.stderr)
        return 1
    sortie.mkdir(parents=True, exist_ok=True)
    for texte in {t for c in declaration["composants"] for t in c["textes"]}:
        shutil.copyfile(RACINE / texte, sortie / pathlib.PurePosixPath(texte).name)
    (sortie / "composants.json").write_text(
        json.dumps({"composants": composants}, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
