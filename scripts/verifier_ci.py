#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
"""La CI prépare Android par UNE action commune, chaque émulateur par son image (#232), et
le pipeline du poste rejoue ce qu'elle juge hors émulateur (#329).

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
- une plateforme de compileSdk installée autre que celle de `build.gradle.kts` ;
- le déclencheur `merge_group:` : aucune file de fusion n'est activée sur `main`, la règle
  est l'inverse (`standards/09` §8 bis : checks requis sans être à jour, chaque commit de
  `main` jugé ensuite) — un déclencheur sans file promet une protection absente (#328) ;
- un écart entre la CI et `scripts/pipeline.sh` (#329) : chaque `run:` du job « Dépôt »
  y figure tel quel, sauf exception nommée de `EXCEPTIONS_POSTE` (une exception qui ne
  sert plus est refusée) ; chaque tâche Gradle du job « Build » y figure ; chaque
  `:<module>:connectedDebugAndroidTest` d'un émulateur y est au moins compilé
  (`:<module>:assembleDebugAndroidTest`). Un pipeline vert au poste dit alors un « Dépôt »
  et un « Build » verts ;
- un job sans l'ALERTE pour dernier pas (#345, `standards/07` §1 bis) : chaque job de chaque
  workflow finit par `python3 -I scripts/signaler_ci.py --signaler`, sous la garde
  `GARDE_ALERTE` (un échec ou une annulation, jamais une PR — une condition de plus peut
  suivre, après `&&`), avec son `GH_TOKEN` ; le workflow déclare `issues: write`. Sans ce
  contrôle, le prochain job naîtrait muet ;
- un émulateur dont le script ne passe pas par `ARRET` (#315) : l'action attend la fin de
  l'émulateur sans délai, un arrêt qui pend tenait le job jusqu'à son `timeout-minutes`.

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
CI = ".github/workflows/ci.yml"
# Le dernier pas de chaque job : un rouge de main ou d'un passage planifié ouvre (ou complète) un
# ticket (#345). La garde exclut la PR dans le pas lui-même ; le script la refuse aussi.
SIGNALER = "python3 -I scripts/signaler_ci.py --signaler"
GARDE_ALERTE = "(failure() || cancelled()) && github.event_name != 'pull_request'"
# Le script de chaque émulateur : la commande, puis l'arrêt borné de l'émulateur (#315).
ARRET = "python3 -I scripts/arreter_emulateur.py --"
PIPELINE = "scripts/pipeline.sh"
# Un pas du job « Dépôt » que le poste rejoue AUTREMENT : son nom → ce que pipeline.sh porte.
EXCEPTIONS_POSTE = {
    # La CI installe reuse à sa version épinglée dans un venv ; le poste lit l'outil installé,
    # par la même écriture que le pre-commit (sans l'outil : INCONNU, jamais vert).
    "REUSE": "bash scripts/reuse-au-poste.sh",
}


def jobs(texte: str) -> dict[str, str]:
    """Les blocs des jobs d'un workflow, par nom (indentation de deux espaces sous `jobs:`)."""
    corps = texte.split("\njobs:\n", 1)
    if len(corps) != 2:
        return {}
    morceaux = re.split(r"^  ([A-Za-z0-9_-]+):\s*$", corps[1], flags=re.M)
    return dict(zip(morceaux[1::2], morceaux[2::2]))


def pas_du_job(bloc: str) -> list[str]:
    """Les pas d'un job, dans l'ordre (une entrée `- ` à six espaces sous `steps:`)."""
    if "\n    steps:\n" not in bloc:
        return []
    return re.split(r"^      - ", bloc.split("\n    steps:\n", 1)[1], flags=re.M)[1:]


def faute_alerte(nom: str, bloc: str) -> str | None:
    """Le dernier pas du job est l'alerte, sous sa garde, avec son jeton (#345)."""
    pas = pas_du_job(bloc)
    if not pas:
        return f"job « {nom} » : aucun pas lisible — son alerte ne se juge pas"
    dernier = pas[-1]
    garde = re.search(r"^\s+if: \$\{\{ (.+?) \}\}\s*$", dernier, re.M)
    if SIGNALER not in dernier:
        return f"job « {nom} » : son dernier pas n'est pas l'alerte « {SIGNALER} » (#345)"
    if not garde or not (garde.group(1) == GARDE_ALERTE or garde.group(1).startswith(GARDE_ALERTE + " && ")):
        return f"job « {nom} » : l'alerte n'a pas la garde « if: ${{{{ {GARDE_ALERTE} }}}} »"
    if not re.search(r"^\s+GH_TOKEN: \$\{\{ github\.token \}\}\s*$", dernier, re.M):
        return f"job « {nom} » : l'alerte sans GH_TOKEN"
    return None


def juger_workflow(chemin: str, texte: str) -> list[str]:
    fautes = [f"{chemin} : « {i} » écrit dans le workflow — il passe par {ACTION}"
              for i in INTERDITS if i in texte]
    blocs = jobs(texte)
    if not blocs:
        return fautes + [f"{chemin} : aucun job lisible"]
    if not re.search(r"^permissions:\n(?:  .*\n)*  issues: write(?:\s+#.*)?$", texte, re.M):
        fautes.append(f"{chemin} : « issues: write » absent des permissions du workflow — l'alerte ne pourrait rien ouvrir")
    for nom, bloc in blocs.items():
        alerte = faute_alerte(nom, bloc)
        if alerte:
            fautes.append(f"{chemin} : {alerte}")
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
        script = re.search(r"^\s+script: (.+?)\s*$", emulateur, re.M)
        if not script or not script.group(1).startswith(ARRET + " "):
            fautes.append(f"{chemin} : job « {nom} » : le script de l'émulateur ne passe pas par « {ARRET} » (arrêt borné, #315)")
        for parametre, variable in IMAGE.items():
            m = re.search(rf"^\s+{parametre}: (.+?)\s*$", emulateur, re.M)
            if not m or m.group(1) != "${{ env.%s }}" % variable:
                fautes.append(f"{chemin} : job « {nom} » : l'émulateur doit lire {parametre}: ${{{{ env.{variable} }}}}")
    if any(EMULATEUR in b for b in blocs.values()) and not re.search(r"^  IMAGE_ARCH: \S+\s*$", texte, re.M):
        fautes.append(f"{chemin} : IMAGE_ARCH absent de l'environnement du workflow")
    return fautes


def pas_run(bloc: str) -> list[tuple[str, str]]:
    """Les pas d'un job : (nom, commande d'une ligne) ; une commande sur plusieurs lignes vaut « | »."""
    pas = []
    for morceau in re.split(r"^      - ", bloc, flags=re.M)[1:]:
        nom = re.search(r"^(?:name: )?(.*)$", morceau.splitlines()[0]).group(1) if morceau.startswith("name: ") else ""
        run = re.search(r"^\s*run: (.+?)\s*$", morceau, re.M)
        if run:
            pas.append((nom, run.group(1)))
    return pas


def juger_pipeline(texte_ci: str, pipeline: str) -> list[str]:
    """Ce que la CI juge hors émulateur, le pipeline du poste le rejoue (#329)."""
    blocs = jobs(texte_ci)
    fautes = []
    if "depot" not in blocs or "build" not in blocs:
        return [f"{CI} : jobs « depot » et « build » attendus"]
    noms = set()
    for nom, run in pas_run(blocs["depot"]):
        if run.startswith(SIGNALER):
            continue  # l'alerte n'est pas un contrôle : rien à rejouer au poste
        noms.add(nom)
        if nom in EXCEPTIONS_POSTE:
            if EXCEPTIONS_POSTE[nom] not in pipeline:
                fautes.append(f"{PIPELINE} : le pas « {nom} » se rejoue par « {EXCEPTIONS_POSTE[nom]} », absent")
        elif run == "|" or run not in pipeline:
            fautes.append(f"{PIPELINE} : le pas « {nom} » du job Dépôt n'est pas rejoué (« {run} »)")
    fautes += [f"{PIPELINE} : exception « {n} » sans pas du job Dépôt — à retirer de EXCEPTIONS_POSTE"
               for n in EXCEPTIONS_POSTE if n not in noms]
    taches = set(re.findall(r"(?<!\S):[a-z-]+:[A-Za-z]+", pipeline))
    for _, run in pas_run(blocs["build"]):
        fautes += [f"{PIPELINE} : tâche {t} du job Build absente"
                   for t in re.findall(r"(?<!\S):[a-z-]+:[A-Za-z]+", run) if t not in taches]
    for m in re.finditer(r"(:[a-z-]+):connectedDebugAndroidTest", texte_ci):
        if f"{m.group(1)}:assembleDebugAndroidTest" not in taches:
            fautes.append(f"{PIPELINE} : {m.group(1)}:assembleDebugAndroidTest absent (ses tests d'interface ne se compilent qu'en CI)")
    return sorted(set(fautes))


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
    dossier = racine / ".github" / "workflows"
    workflows = sorted(dossier.glob("*.yml")) + sorted(dossier.glob("*.yaml"))
    if not workflows:
        fautes.append(".github/workflows : aucun workflow")
    for w in workflows:
        texte = w.read_text(encoding="utf-8")
        fautes += juger_workflow(w.relative_to(racine).as_posix(), texte)
        if re.search(r"^  merge_group:", texte, re.M):
            fautes.append(f"{w.relative_to(racine).as_posix()} : « merge_group: » sans file de fusion sur main (standards/09 §8 bis, #328)")
    ci, pipeline = racine / CI, racine / PIPELINE
    if not ci.is_file() or not pipeline.is_file():
        return fautes + [f"{CI} ou {PIPELINE} absent"]
    return fautes + juger_pipeline(ci.read_text(encoding="utf-8"), pipeline.read_text(encoding="utf-8"))


ALERTE_BONNE = """      - name: Alerte
        if: ${{ (failure() || cancelled()) && github.event_name != 'pull_request' }}
        env:
          GH_TOKEN: ${{ github.token }}
        run: python3 -I scripts/signaler_ci.py --signaler --etat "${{ job.status }}"
"""
BON = """name: CI
permissions:
  contents: read
  issues: write # pour l'alerte
env:
  IMAGE_ARCH: x86_64
jobs:
  depot:
    runs-on: ubuntu-latest
    steps:
      - name: Garde-fous du dépôt
        run: bash scripts/verifier-depot.sh
      - name: REUSE
        run: |
          reuse lint
""" + ALERTE_BONNE + """  build:
    steps:
      - uses: ./.github/actions/preparer-android
      - run: ./gradlew :app:assembleDebug
""" + ALERTE_BONNE + """  interface:
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
          script: python3 -I scripts/arreter_emulateur.py -- ./gradlew :app:connectedDebugAndroidTest
""" + ALERTE_BONNE
PIPELINE_BON = """bash scripts/verifier-depot.sh
bash scripts/reuse-au-poste.sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
"""


def autotest() -> bool:
    fautifs = {
        "setup-gradle écrit": BON.replace("      - run: ./gradlew :app:assembleDebug",
                                          "      - uses: gradle/actions/setup-gradle@x\n      - run: ./gradlew :app:assembleDebug"),
        "sdkmanager écrit": BON.replace("      - uses: reactivecircus/", "      - run: sdkmanager --install emulator\n      - uses: reactivecircus/"),
        "Gradle sans l'action": BON.replace("  build:\n    steps:\n      - uses: ./.github/actions/preparer-android\n",
                                            "  build:\n    steps:\n"),
        "sans emulateur: oui": BON.replace("          emulateur: oui\n", "          emulateur: non\n"),
        "image écrite": BON.replace("api-level: ${{ env.IMAGE_API }}", "api-level: 35"),
        "cible absente": BON.replace("      IMAGE_CIBLE: google_apis\n", ""),
        "arch absente": BON.replace("env:\n  IMAGE_ARCH: x86_64\n", ""),
        "file de fusion": BON.replace("name: CI\n", "name: CI\non:\n  merge_group:\n"),
        "pas du Dépôt non rejoué": BON.replace("      - name: REUSE\n", "      - name: Contrat\n        run: bash scripts/importer-contrat.sh --verifier\n      - name: REUSE\n"),
        "exception sans pas": BON.replace("      - name: REUSE\n        run: |\n          reuse lint\n", ""),
        # #345 : un job ajouté sans l'alerte, une alerte qui n'est plus le dernier pas, sans sa
        # garde (elle jouerait sur une PR), sans jeton, et un workflow sans « issues: write ».
        "job sans alerte": BON + "  ajoute:\n    runs-on: ubuntu-latest\n    steps:\n      - run: echo\n",
        "alerte pas en dernier": BON.replace(ALERTE_BONNE + "  interface:", ALERTE_BONNE + "      - run: echo après\n  interface:"),
        "alerte sans garde de PR": BON.replace(ALERTE_BONNE + "  build:", ALERTE_BONNE.replace(
            " && github.event_name != 'pull_request'", "") + "  build:"),
        "alerte sans jeton": BON.replace(ALERTE_BONNE + "  build:", ALERTE_BONNE.replace(
            "          GH_TOKEN: ${{ github.token }}\n", "          X: y\n") + "  build:"),
        "sans issues: write": BON.replace("  issues: write # pour l'alerte\n", ""),
        # #315 : un émulateur dont le script ne passe pas par l'arrêt borné.
        "émulateur sans arrêt borné": BON.replace("script: python3 -I scripts/arreter_emulateur.py -- ./gradlew",
                                                  "script: ./gradlew"),
    }
    pipelines_fautifs = {
        "tâche du Build absente": PIPELINE_BON.replace(":app:assembleDebug :", ":"),
        "tests d'interface non compilés": PIPELINE_BON.replace(" :app:assembleDebugAndroidTest", ""),
        "REUSE du poste absent": PIPELINE_BON.replace("bash scripts/reuse-au-poste.sh\n", ""),
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
        ci = racine / CI
        pipeline = racine / PIPELINE
        pipeline.parent.mkdir(parents=True)
        pipeline.write_text(PIPELINE_BON, encoding="utf-8")
        ci.write_text(BON, encoding="utf-8")
        if juger(racine) != []:
            print(f"autotest : le cas propre est refusé : {juger(racine)}")
            ok = False
        for nom, texte in fautifs.items():
            ci.write_text(texte, encoding="utf-8")
            if len(juger(racine)) != 1:
                print(f"autotest : « {nom} » non refusé exactement une fois : {juger(racine)}")
                ok = False
        ci.write_text(BON, encoding="utf-8")
        for nom, texte in pipelines_fautifs.items():
            pipeline.write_text(texte, encoding="utf-8")
            if len(juger(racine)) != 1:
                print(f"autotest : « {nom} » non refusé exactement une fois : {juger(racine)}")
                ok = False
        pipeline.write_text(PIPELINE_BON, encoding="utf-8")
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
    print("✅ CI : chaque job Gradle passe par la préparation commune, chaque émulateur par son image et son arrêt borné ;"
          " chaque job finit par l'alerte ; le poste rejoue Dépôt et Build")
    return 0


if __name__ == "__main__":
    sys.exit(main())
