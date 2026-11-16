#!/bin/bash
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
#
# pipeline.sh — LA porte de Gradle au poste. Tout `./gradlew` lourd (assemble, test,
# lint, connectedCheck) passe par ici, sous le verrou commun du poste (#55) : une tâche
# lourde à la fois, iOS compris. Un Gradle lancé en direct a tourné en même temps qu'un
# xcodebuild (constat du coordinateur, 16/11/2026).
#
#   bash scripts/pipeline.sh                 le pipeline de la CI hors émulateur, puis
#                                            verifier-depot.sh et reuse lint
#   bash scripts/pipeline.sh <tâches Gradle> ces tâches seules (ex. :app:testDebugUnitTest --tests '*Fichiers*')
#
# Les tests d'interface ont leur script, tests-interface.sh, sous le même verrou.
set -uo pipefail
cd "$(dirname "$0")/.."
[ -n "${VERROU_POSTE_TENU:-}" ] \
  || exec bash scripts/sous-verrou.sh "Gradle Android (pipeline.sh)" -- bash scripts/pipeline.sh "$@"

# Deux workers au poste partagé, sur le démon commun (gradle.properties) — jamais --no-daemon.
if [ $# -gt 0 ]; then
  exec ./gradlew -q --max-workers=2 "$@"
fi
./gradlew -q --max-workers=2 :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease \
  :app:assembleDebugAndroidTest :client-api:test || exit 1
bash scripts/verifier-depot.sh || exit 1
if command -v reuse >/dev/null; then
  reuse lint >/dev/null || { echo "❌ reuse lint"; exit 1; }
else
  echo "❓ reuse absent : REUSE non vérifié au poste (la CI le rejoue)"
fi
echo "✅ pipeline du poste vert"
