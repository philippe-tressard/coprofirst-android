#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
"""Les composants tiers que l'app EMBARQUE, pour « À propos » (#27) — comme l'iOS
(`ComposantsTiers`, tenu par `scripts/verifier-dependances.py`).

La déclaration (`app/src/main/tiers/composants.json`) nomme chaque composant, ses
licences, son rôle, les modules Maven qu'il couvre (motifs `groupe:module`, `*` admis) ou
les fichiers du dépôt qu'il embarque (`fichiers`),
et les textes qui doivent accompagner chaque copie : l'avis de ses auteurs, puis la
licence entière. La déclaration est COMMUNE au téléphone et à la montre (#317) : même app
Google Play. Gradle donne, pour chaque app, les modules de sa variante publiée
(`releaseRuntimeClasspath`) et ce que chacun requiert (`groupe:module`, une tabulation, les
modules requis) ; ce script confronte la liste de l'app construite à la déclaration, et
s'ARRÊTE :

- sur un module embarqué qu'aucun composant ne couvre, ou que deux couvrent ;
- sur un motif qui ne couvre plus rien dans AUCUNE des deux apps (un composant retiré
  reste déclaré) ;
- sur une bibliothèque Google (`com.google.android.gms`, `com.google.firebase`) que ne
  requièrent pas, de proche en proche, les bibliothèques que nomme le point 4 de la licence
  (#320 : « ainsi que les bibliothèques de Google que ces deux-là requièrent ») ;
- sur une licence sans son texte, ou un texte introuvable. Le texte d'une licence est
  `LICENSES/<id>.txt` quand un fichier du dépôt l'emploie, sinon
  `app/src/main/tiers/LICENSE-<id>.txt` : REUSE refuse dans `LICENSES/` une licence
  qu'aucun fichier du dépôt n'emploie (la Public Suffix List n'est que dans l'APK).

Une nomenclature (`*-bom`) n'embarque aucun code : elle est écartée.

Il écrit dans `<sortie>/tiers/` la déclaration — les composants des deux apps, enrichis de
leurs modules — et une copie de chaque texte : ce que « À propos » du téléphone lit, et ce
que chaque copie de la montre emporte.

Usage : generer_composants_tiers.py <composants.json> <dossier de sortie> <modules.txt> [<modules de l'autre app>…]
"""

from __future__ import annotations

import fnmatch
import importlib.util
import json
import pathlib
import re
import shutil
import sys

RACINE = pathlib.Path(__file__).resolve().parent.parent
DOSSIER = "tiers"


def _juge_google():
    """Le juge des dépendances Google (point 4 de la licence), chargé par son chemin (`-I`)."""
    spec = importlib.util.spec_from_file_location("verifier_dependances_google",
                                                  RACINE / "scripts" / "verifier_dependances_google.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


GOOGLE = _juge_google()


def textes_de_licence(licence: str) -> tuple[str, str]:
    """Où peut se trouver le texte d'une licence : dans `LICENSES/`, ou à côté de la déclaration."""
    return f"LICENSES/{licence}.txt", f"app/src/main/tiers/LICENSE-{licence}.txt"


class DeclarationInvalide(Exception):
    pass


def graphe(lignes: list[str]) -> dict[str, set[str]]:
    """Chaque module embarqué et ceux qu'il requiert, depuis `groupe:module<TAB>requis…`."""
    requis: dict[str, set[str]] = {}
    for ligne in lignes:
        module, _, enfants = ligne.strip().partition("\t")
        if module:
            requis.setdefault(module, set()).update(enfants.split())
    return requis


def embarques(lignes: list[str]) -> list[str]:
    """Les modules `groupe:module` embarqués, sans les nomenclatures."""
    return [m for m in sorted(graphe(lignes)) if not m.split(":")[-1].endswith("-bom")]


def google_non_permises(requis: dict[str, set[str]], permises: set[str]) -> list[str]:
    """Les bibliothèques Google embarquées que les bibliothèques permises ne requièrent pas."""
    atteintes, a_voir = set(), [m for m in permises if m in requis]
    while a_voir:
        m = a_voir.pop()
        if m not in atteintes:
            atteintes.add(m)
            a_voir += requis.get(m, ())
    famille = re.compile(rf"^{GOOGLE.FAMILLES}:")
    return sorted(m for m in requis if famille.match(m) and m not in atteintes)


def verifier(declaration: dict, modules: list[str], existe, autres: list[str] = ()) -> list[dict]:
    """Les composants, chacun avec ses modules ; `existe(chemin)` dit si un texte est là.

    `modules` : ceux de l'app construite, qui doivent TOUS être couverts ; `autres` : ceux de
    l'autre app, qui comptent pour juger un motif mort et ce que la sortie nomme."""
    tous = sorted(set(modules) | set(autres))
    composants = declaration.get("composants")
    if not isinstance(composants, list) or not composants:
        raise DeclarationInvalide("aucun composant déclaré")
    erreurs: list[str] = []
    couverts: dict[str, list[str]] = {}
    sortie = []
    for c in composants:
        nom = c.get("nom")
        motifs, licences, textes = c.get("modules") or [], c.get("licences") or [], c.get("textes") or []
        # Un composant qui n'est pas un module Maven : un fichier du dépôt que l'app embarque
        # (les tracés des icônes du site, #183).
        fichiers = c.get("fichiers") or []
        if not nom or not c.get("role") or not (motifs or fichiers) or not licences or not textes:
            raise DeclarationInvalide(f"composant incomplet : {c}")
        siens = []
        for motif in motifs:
            trouves = [m for m in tous if fnmatch.fnmatchcase(m, motif)]
            if not trouves:
                erreurs.append(f"{nom} : le motif « {motif} » ne couvre plus aucun module embarqué")
            siens += trouves
        for m in siens:
            couverts.setdefault(m, []).append(nom)
        for licence in licences:
            if not set(textes_de_licence(licence)) & set(textes):
                erreurs.append(f"{nom} : la licence {licence} sans son texte ({' ou '.join(textes_de_licence(licence))})")
        erreurs += [f"{nom} : texte introuvable, {t}" for t in textes if not existe(t)]
        erreurs += [f"{nom} : fichier embarqué introuvable, {f}" for f in fichiers if not existe(f)]
        sortie.append({"nom": nom, "licences": licences, "role": c["role"], "modules": sorted(set(siens)) + list(fichiers),
                       "textes": [pathlib.PurePosixPath(t).name for t in textes]})
    erreurs += [f"module embarqué sans composant déclaré : {m}" for m in modules if m not in couverts]
    erreurs += [f"{m} couvert par deux composants : {', '.join(n)}" for m, n in couverts.items() if len(n) > 1]
    if erreurs:
        raise DeclarationInvalide("\n  ".join(["la déclaration ne dit pas ce que l'app embarque :"] + erreurs))
    return sortie


def main(argv: list[str]) -> int:
    if len(argv) < 4:
        print(__doc__.strip().splitlines()[-1], file=sys.stderr)
        return 2
    entree, sortie = pathlib.Path(argv[1]), pathlib.Path(argv[2]) / DOSSIER
    listes = [pathlib.Path(a).read_text(encoding="utf-8").splitlines() for a in argv[3:]]
    declaration = json.loads(entree.read_text(encoding="utf-8"))
    try:
        permises = GOOGLE.autorisees(GOOGLE.LICENCE.read_text(encoding="utf-8"))
        hors_licence = google_non_permises(graphe(listes[0]), permises)
        if hors_licence:
            raise DeclarationInvalide("bibliothèques Google que la licence ne permet pas (point 4), requises par "
                                      "autre chose que " + ", ".join(sorted(permises)) + " :\n  " + "\n  ".join(hors_licence))
        composants = verifier(declaration, embarques(listes[0]), lambda t: (RACINE / t).is_file(),
                              [m for liste in listes[1:] for m in embarques(liste)])
    except (DeclarationInvalide, ValueError) as e:
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
