#!/bin/bash
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
#
# pipeline.sh — LA porte de Gradle au poste. Tout `./gradlew` lourd (assemble, test,
# lint, connectedCheck) passe par ici, sous le verrou commun du poste (#55) : une tâche
# lourde à la fois, iOS compris. Un Gradle lancé en direct a tourné en même temps qu'un
# xcodebuild (constat du coordinateur, 16/11/2026).
#
#   bash scripts/pipeline.sh                 la CI hors émulateur : le job « Dépôt » (hors du
#                                            verrou : il ne lance pas Gradle), puis les tâches
#                                            du job « Build » et la compilation des tests
#                                            d'interface du téléphone et de la montre
#   bash scripts/pipeline.sh <tâches Gradle> ces tâches seules (ex. :app:testDebugUnitTest --tests '*Fichiers*')
#
# `scripts/verifier_ci.py` le tient égal à ci.yml (#329) : un pas ajouté à la CI sans être
# rejoué ici est refusé. Ce qui ne tourne qu'en CI : les tests d'interface eux-mêmes (ils ont
# leur script, tests-interface.sh, sous le même verrou).
set -uo pipefail
cd "$(dirname "$0")/.."
# Le job « Dépôt », d'abord et hors du verrou. Les trois recopies lisent le réseau et `gh` :
# sans eux, INCONNU (sortie 2) — le pipeline s'arrête, il ne se dit jamais vert.
if [ $# -eq 0 ] && [ -z "${PIPELINE_DEPOT_JUGE:-}" ]; then
  bash scripts/verifier-depot.sh || exit $?
  bash scripts/importer-contrat.sh --verifier || exit $?
  bash scripts/importer-logo.sh --verifier || exit $?
  bash scripts/importer-vitrine.sh --verifier || exit $?
  bash scripts/reuse-au-poste.sh || exit $?
  export PIPELINE_DEPOT_JUGE=1
fi
[ -n "${VERROU_POSTE_TENU:-}" ] \
  || exec bash scripts/sous-verrou.sh "Gradle Android (pipeline.sh)" -- bash scripts/pipeline.sh "$@"

# Deux workers au poste partagé, sur le démon commun (gradle.properties) — jamais --no-daemon.
if [ $# -gt 0 ]; then
  exec ./gradlew -q --max-workers=2 "$@"
fi
./gradlew -q --max-workers=2 :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease \
  :app:assembleDebugAndroidTest :client-api:test :partage:test :partage-android:lintDebug :partage-android:testDebugUnitTest \
  :montre:testDebugUnitTest :montre:lintDebug :montre:assembleDebug :montre:assembleRelease \
  :montre:assembleDebugAndroidTest || exit 1
echo "✅ pipeline du poste vert"
