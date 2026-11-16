#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
"""La CI prépare Android par UNE action commune, et chaque émulateur par son image (#232).

Un rouge d'environnement (429 de Maven Central, image système tronquée) bloque une fusion
dès que le job est requis. La préparation commune (`.github/actions/preparer-android`) tient
l'image en cache et la télécharge avec un réessai borné (`scripts/installer-sdk.sh`) ; elle
ne protège que les jobs qui l'appellent, et que l'image qu'on lui nomme. Ce juge refuse :

- `setup-java`, `setup-gradle` ou `sdkmanager` écrits dans un workflow : un job préparé à
  part retéléchargerait sans réessai ;
- un job qui lance `./gradlew` sans l'action commune ;
- un job d'émulateur qui ne demande pas l'image (`emulateur: oui`), ne la nomme pas dans son
  environnement (IMAGE_API, IMAGE_CIBLE), ou donne à l'émulateur une autre image que
  `${{ env.IMAGE_… }}` — l'émulateur la retéléchargerait, sans cache ni réessai ;
- une plateforme de compileSdk installée autre que celle de `build.gradle.kts`.

Sortie : 0 conforme, 1 refus, 2 INCONNU. `--autotest` prouve que le juge départage.
"""

from __future__ import annotations

import pathlib
import re
import sys
import tempfile

RACINE = pathlib.Path(__file__).resolve().parent.parent
ACTION = ".github/actions/preparer-android/action.yml"
INTERDITS = ("actions/setup-java", "gradle/actions/setup-gradle", "sdkmanager")
EMULATEUR = "reactivecircus/android-emulator-runner"
IMAGE = {"api-level": "IMAGE_API", "target": "IMAGE_CIBLE", "arch": "IMAGE_ARCH"}


def jobs(texte: str) -> dict[str, str]:
    """Les blocs des jobs d'un workflow, par nom (indentation de deux espaces sous `jobs:`)."""
    corps = texte.split("\njobs:\n", 1)
    if len(corps) != 2:
        return {}
    morceaux = re.split(r"^  ([A-Za-z0-9_-]+):\s*$", corps[1], flags=re.M)
    return dict(zip(morceaux[1::2], morceaux[2::2]))


def juger_workflow(chemin: str, texte: str) -> list[str]:
    fautes = [f"{chemin} : « {i} » écrit dans le workflow — il passe par {ACTION}"
              for i in INTERDITS if i in texte]
    blocs = jobs(texte)
    if not blocs:
        return fautes + [f"{chemin} : aucun job lisible"]
    for nom, bloc in blocs.items():
        prepare = "uses: ./.github/actions/preparer-android" in bloc
        if "./gradlew" in bloc and not prepare:
            fautes.append(f"{chemin} : job « {nom} » lance Gradle sans {ACTION}")
        if EMULATEUR not in bloc:
            continue
        if not re.search(r"^\s+emulateur: oui\s*$", bloc, re.M):
            fautes.append(f"{chemin} : job « {nom} » : émulateur sans « emulateur: oui » (image ni en cache ni réessayée)")
        for cle in ("IMAGE_API", "IMAGE_CIBLE"):
            if not re.search(rf"^      {cle}: \S+\s*$", bloc, re.M):
                fautes.append(f"{chemin} : job « {nom} » : {cle} absent de son environnement")
        emulateur = bloc.split(EMULATEUR, 1)[1]
        for parametre, variable in IMAGE.items():
            m = re.search(rf"^\s+{parametre}: (.+?)\s*$", emulateur, re.M)
            if not m or m.group(1) != "${{ env.%s }}" % variable:
                fautes.append(f"{chemin} : job « {nom} » : l'émulateur doit lire {parametre}: ${{{{ env.{variable} }}}}")
    if any(EMULATEUR in b for b in blocs.values()) and not re.search(r"^  IMAGE_ARCH: \S+\s*$", texte, re.M):
        fautes.append(f"{chemin} : IMAGE_ARCH absent de l'environnement du workflow")
    return fautes


def plateforme_du_build(texte: str) -> str | None:
    m = re.search(r"release\((\d+)\)\s*\{\s*minorApiLevel\s*=\s*(\d+)\s*\}", texte)
    if m:
        return f"android-{m.group(1)}.{m.group(2)}"
    m = re.search(r"compileSdk\s*=\s*(\d+)\b", texte)
    return f"android-{m.group(1)}" if m else None


def juger(racine: pathlib.Path) -> list[str]:
    action = racine / ACTION
    if not action.is_file():
        return [f"{ACTION} absente"]
    texte_action = action.read_text(encoding="utf-8")
    fautes = []
    attendue = plateforme_du_build((racine / "build.gradle.kts").read_text(encoding="utf-8"))
    installee = re.search(r'installer-sdk\.sh "platforms;(android-[0-9.]+)"', texte_action)
    if attendue is None:
        fautes.append("build.gradle.kts : compileSdk illisible")
    elif not installee or installee.group(1) != attendue:
        fautes.append(f"{ACTION} : plateforme de compileSdk {installee.group(1) if installee else '?'}, le build dit {attendue}")
    workflows = sorted((racine / ".github" / "workflows").glob("*.yml"))
    if not workflows:
        fautes.append(".github/workflows : aucun workflow")
    for w in workflows:
        fautes += juger_workflow(w.relative_to(racine).as_posix(), w.read_text(encoding="utf-8"))
    return fautes


BON = """name: CI
env:
  IMAGE_ARCH: x86_64
jobs:
  depot:
    runs-on: ubuntu-latest
    steps:
      - run: bash scripts/verifier-depot.sh
  build:
    steps:
      - uses: ./.github/actions/preparer-android
      - run: ./gradlew :app:assembleDebug
  interface:
    env:
      IMAGE_API: 35
      IMAGE_CIBLE: google_apis
    steps:
      - uses: ./.github/actions/preparer-android
        with:
          emulateur: oui
      - uses: reactivecircus/android-emulator-runner@a # v2
        with:
          api-level: ${{ env.IMAGE_API }}
          target: ${{ env.IMAGE_CIBLE }}
          arch: ${{ env.IMAGE_ARCH }}
          script: ./gradlew :app:connectedDebugAndroidTest
"""


def autotest() -> bool:
    fautifs = {
        "setup-gradle écrit": BON.replace("      - run: ./gradlew :app:assembleDebug",
                                          "      - uses: gradle/actions/setup-gradle@x\n      - run: ./gradlew :app:assembleDebug"),
        "sdkmanager écrit": BON.replace("bash scripts/verifier-depot.sh", "sdkmanager --install emulator"),
        "Gradle sans l'action": BON.replace("  build:\n    steps:\n      - uses: ./.github/actions/preparer-android\n",
                                            "  build:\n    steps:\n"),
        "sans emulateur: oui": BON.replace("          emulateur: oui\n", "          emulateur: non\n"),
        "image écrite": BON.replace("api-level: ${{ env.IMAGE_API }}", "api-level: 35"),
        "cible absente": BON.replace("      IMAGE_CIBLE: google_apis\n", ""),
        "arch absente": BON.replace("env:\n  IMAGE_ARCH: x86_64\n", ""),
    }
    build = "android { compileSdk { version = release(37) { minorApiLevel = 2 } } }\n"
    action = 'run: bash scripts/installer-sdk.sh "platforms;android-37.2"\n'
    ok = True
    with tempfile.TemporaryDirectory() as d:
        racine = pathlib.Path(d)
        (racine / ".github" / "workflows").mkdir(parents=True)
        (racine / ACTION).parent.mkdir(parents=True)
        (racine / "build.gradle.kts").write_text(build, encoding="utf-8")
        (racine / ACTION).write_text(action, encoding="utf-8")
        ci = racine / ".github" / "workflows" / "ci.yml"
        ci.write_text(BON, encoding="utf-8")
        ok &= juger(racine) == []
        for nom, texte in fautifs.items():
            ci.write_text(texte, encoding="utf-8")
            if len(juger(racine)) != 1:
                print(f"autotest : « {nom} » non refusé exactement une fois : {juger(racine)}")
                ok = False
        ci.write_text(BON, encoding="utf-8")
        (racine / ACTION).write_text(action.replace("37.2", "36"), encoding="utf-8")
        ok &= len(juger(racine)) == 1
    return ok


def main() -> int:
    if sys.argv[1:] == ["--autotest"]:
        bon = autotest()
        print("✅ autotest de la préparation de la CI" if bon else "❌ autotest de la préparation de la CI en échec")
        return 0 if bon else 1
    try:
        fautes = juger(RACINE)
    except OSError as e:
        print(f"❓ préparation de la CI INCONNUE ({e})")
        return 2
    for faute in fautes:
        print(f"❌ {faute}")
    if fautes:
        return 1
    print("✅ CI : chaque job Gradle passe par la préparation commune, chaque émulateur par son image")
    return 0


if __name__ == "__main__":
    sys.exit(main())
