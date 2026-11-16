#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
#
# Installe des paquets du SDK Android avec un réessai BORNÉ (#232) : un téléchargement
# tronqué (« Error reading Zip content »), un 429 ou une coupure ne font plus tomber un
# job de la CI au premier essai. Un paquet se juge INSTALLÉ par son `package.xml`, jamais
# sur le seul code de sortie de sdkmanager ; un paquet déjà présent (cache restauré,
# image du runner) ne se télécharge pas.
#
#   bash scripts/installer-sdk.sh "system-images;android-35;google_apis;x86_64" emulator
#
# Réglages, pour les tests (scripts/test_installer_sdk.py) : SDKMANAGER, ESSAIS (4),
# ATTENTE_REESSAI (15 s, doublée à chaque essai : 15 + 30 + 60 s au plus).
set -euo pipefail

[ $# -gt 0 ] || { echo "usage : bash scripts/installer-sdk.sh <paquet>…" >&2; exit 2; }
: "${ANDROID_HOME:?manque}"
SDKMANAGER=${SDKMANAGER:-$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager}
ESSAIS=${ESSAIS:-4}
attente=${ATTENTE_REESSAI:-15}

for ((essai = 1; ; essai++)); do
  reste=()
  for paquet in "$@"; do
    [ -f "$ANDROID_HOME/${paquet//;//}/package.xml" ] || reste+=("$paquet")
  done
  if [ ${#reste[@]} -eq 0 ]; then
    echo "✅ SDK : $* — installé"
    exit 0
  fi
  if [ "$essai" -gt "$ESSAIS" ]; then
    echo "❌ SDK : ${reste[*]} — toujours absent après $ESSAIS essais" >&2
    exit 1
  fi
  if [ "$essai" -gt 1 ]; then
    echo "↻ SDK : ${reste[*]} — nouvel essai ($essai/$ESSAIS) dans $attente s"
    sleep "$attente"
    attente=$((attente * 2))
  fi
  # Les licences s'acceptent au fil de l'installation ; `yes` coupé par la fin de
  # sdkmanager ne compte pas comme un échec (pipefail).
  { yes || true; } | "$SDKMANAGER" --install "${reste[@]}" > /dev/null \
    || echo "⚠️ sdkmanager en échec (essai $essai/$ESSAIS)" >&2
done
