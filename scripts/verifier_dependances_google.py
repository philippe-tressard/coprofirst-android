#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
"""Aucune bibliothèque Google Play services ni Firebase que la licence ne nomme pas.

La permission additionnelle (point 4 de LICENSES/LicenseRef-CoproFirst-Magasins.txt,
accordée le 16/11/2026, restreinte le 17/11/2026, rouverte à Firebase Cloud Messaging le
18/11/2026 — « voie A », #280) autorise la liaison avec deux bibliothèques seulement :
Firebase Cloud Messaging et Wearable. Une autre bibliothèque de ces familles — Firebase
Analytics, Crashlytics, une publicité — serait liée sans permission : le code distribué
violerait sa propre licence.

La liste autorisée se LIT dans le texte de la licence, jamais recopiée ici : le droit
et le contrôle ne peuvent pas diverger. Sont jugées les dépendances DÉCLARÉES par le
dépôt (`gradle/libs.versions.toml` et chaque `build.gradle.kts` suivi par git). Ce que
l'app EMBARQUE se juge au build (#320) : `generer_composants_tiers.py` refuse toute
bibliothèque de ces familles que les deux permises ne requièrent pas, de proche en proche,
dans le graphe de la variante publiée de chaque app — il lit ce juge (`autorisees`).

Sortie : 0 conforme, 1 refus, 2 INCONNU. `--autotest` prouve que le juge départage.
"""

from __future__ import annotations

import pathlib
import re
import subprocess
import sys
import tempfile

RACINE = pathlib.Path(__file__).resolve().parent.parent
LICENCE = RACINE / "LICENSES" / "LicenseRef-CoproFirst-Magasins.txt"
FAMILLES = r"com\.google\.(?:android\.gms|firebase)"
# Une coordonnée « groupe:module », écrite d'un bloc ou en `group = … , name = …`.
COORDONNEE = re.compile(rf"({FAMILLES}):([A-Za-z0-9_.-]+)")
GROUPE_NOM = re.compile(rf'group\s*=\s*"({FAMILLES})"\s*,\s*name\s*=\s*"([A-Za-z0-9_.-]+)"')
# Un groupe de ces familles nommé seul : une forme que ce juge ne sait pas lire.
GROUPE = re.compile(rf'"({FAMILLES})(?::|")')


def autorisees(texte: str) -> set[str]:
    """Les coordonnées nommées au point 4 de la permission (sa liste à tirets)."""
    point = re.search(r"^4\. Bibliothèques Google Play services.*?(?=^\d+\. )", texte, re.S | re.M)
    if not point:
        raise ValueError("point 4 (Bibliothèques Google Play services) introuvable dans la permission")
    noms = {f"{g}:{m}" for g, m in re.findall(rf"^\s*- ({FAMILLES}):([A-Za-z0-9_.-]+)", point.group(0), re.M)}
    if not noms:
        raise ValueError("le point 4 ne nomme aucune bibliothèque")
    return noms


def declarees(chemin: str, texte: str) -> tuple[set[str], list[str]]:
    """Les coordonnées de ces familles déclarées par un fichier, et les formes illisibles."""
    trouvees = {f"{g}:{m}" for g, m in COORDONNEE.findall(texte)} | {f"{g}:{m}" for g, m in GROUPE_NOM.findall(texte)}
    illisibles = []
    for numero, ligne in enumerate(texte.splitlines(), 1):
        if GROUPE.search(ligne) and not (COORDONNEE.search(ligne) or GROUPE_NOM.search(ligne)):
            illisibles.append(f"{chemin}:{numero} : forme de dépendance illisible — {ligne.strip()[:90]}")
    return trouvees, illisibles


def juger(racine: pathlib.Path, permises: set[str]) -> list[str]:
    # Les fichiers que git CONNAÎT (#320) : au poste, les copies de .claude/worktrees/ des
    # autres sessions ne se jugent pas ici. Hors d'un dépôt git (l'autotest), l'arbre.
    suivis = subprocess.run(["git", "ls-files", "--cached"], cwd=racine, capture_output=True, text=True)
    if suivis.returncode == 0:
        builds = sorted(racine / f for f in suivis.stdout.splitlines() if f.endswith("build.gradle.kts"))
    else:
        builds = sorted(racine.rglob("build.gradle.kts"))
    fichiers = [racine / "gradle" / "libs.versions.toml", *builds]
    fautes = []
    for f in fichiers:
        if not f.is_file() or "/build/" in f.relative_to(racine).as_posix():
            continue
        chemin = f.relative_to(racine).as_posix()
        trouvees, illisibles = declarees(chemin, f.read_text(encoding="utf-8"))
        fautes += illisibles
        fautes += [f"{chemin} : {c} n'est pas nommée par la permission (point 4)" for c in sorted(trouvees - permises)]
    return fautes


def autotest() -> bool:
    licence = ("4. Bibliothèques Google Play services (x)\n\n   - com.google.firebase:firebase-messaging (a) ;\n"
               "   - com.google.android.gms:play-services-wearable (b) ;\n\n5. Versions modifiées\n")
    ok = autorisees(licence) == {"com.google.firebase:firebase-messaging", "com.google.android.gms:play-services-wearable"}
    try:
        autorisees("4. Bibliothèques Google Play services\n\n5. Versions\n")
        ok = False
    except ValueError:
        pass
    permises = autorisees(licence)
    cas = {
        'firebase-messaging = { module = "com.google.firebase:firebase-messaging", version.ref = "f" }': 0,
        'wearable = { group = "com.google.android.gms", name = "play-services-wearable", version = "1" }': 0,
        'ads = { module = "com.google.android.gms:play-services-ads", version = "1" }': 1,
        'analytique = { group = "com.google.firebase", name = "firebase-analytics", version = "1" }': 1,
        'implementation("com.google.firebase:firebase-crashlytics:19.0.0")': 1,
        'bom = { group = "com.google.firebase", module = "x" }': 1,
        'coroutines = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core" }': 0,
        'id("com.google.gms.google-services")': 0,
    }
    with tempfile.TemporaryDirectory() as d:
        racine = pathlib.Path(d)
        (racine / "gradle").mkdir()
        for ligne, attendu in cas.items():
            (racine / "gradle" / "libs.versions.toml").write_text(ligne + "\n", encoding="utf-8")
            ok &= len(juger(racine, permises)) == attendu
        # La permission RÉELLE : depuis le 18/11/2026, Firebase Cloud Messaging et Wearable
        # (#280, voie A). Messaging est permise ; une autre bibliothèque Firebase, refusée.
        reelles = autorisees(LICENCE.read_text(encoding="utf-8"))
        (racine / "gradle" / "libs.versions.toml").write_text(
            'firebase-messaging = { module = "com.google.firebase:firebase-messaging", version.ref = "f" }\n', encoding="utf-8")
        ok &= len(juger(racine, reelles)) == 0
        (racine / "gradle" / "libs.versions.toml").write_text(
            'analytique = { module = "com.google.firebase:firebase-analytics", version.ref = "f" }\n', encoding="utf-8")
        ok &= len(juger(racine, reelles)) == 1
        (racine / "gradle" / "libs.versions.toml").write_text(
            'wearable = { group = "com.google.android.gms", name = "play-services-wearable", version = "1" }\n', encoding="utf-8")
        ok &= len(juger(racine, reelles)) == 0
    return ok


def main() -> int:
    if sys.argv[1:] == ["--autotest"]:
        bon = autotest()
        print("✅ autotest des dépendances Google" if bon else "❌ autotest des dépendances Google en échec")
        return 0 if bon else 1
    try:
        permises = autorisees(LICENCE.read_text(encoding="utf-8"))
    except (OSError, ValueError) as e:
        print(f"❓ permission illisible ({e}) : dépendances Google INCONNUES")
        return 2
    fautes = juger(RACINE, permises)
    for faute in fautes:
        print(f"❌ {faute}")
    if fautes:
        return 1
    print(f"✅ dépendances Google : seules celles que la licence nomme ({len(permises)}) sont permises")
    return 0


if __name__ == "__main__":
    sys.exit(main())
