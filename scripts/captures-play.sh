#!/bin/bash
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
#
# captures-play.sh — les visuels de la fiche du Play Store (#31) : l'icône 512, l'image
# de présentation 1024 × 500 et les écrans 1080 × 1920, produits par l'app sur
# l'émulateur (androidTest `CapturesPlay`, `-e captures oui`) et rapatriés dans
# build/captures-play/ — jamais versionnés : ils se refont à chaque version.
# Mêmes règles que tests-interface.sh (une seule tâche lourde au poste).
set -uo pipefail
cd "$(dirname "$0")/.."
SORTIE=build/captures-play
rm -rf "$SORTIE"
ARGUMENTS="-e captures oui" \
RAPATRIER="/sdcard/Android/data/fr.coprofirst.app/files/captures:$SORTIE" \
  bash scripts/tests-interface.sh fr.coprofirst.app.magasin.CapturesPlay
code=$?
ls "$SORTIE" 2>/dev/null
exit $code
