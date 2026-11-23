#!/bin/bash
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
#
# pret-a-envoyer.sh <version> <aab>… — la dernière porte de construire-release.sh (#322) : un
# AAB ne reste à envoyer que si le commit construit porte l'étiquette `v<version>`, que seul le
# contrôle avant soumission pose (`controle_avant_soumission.py --taguer`, #4), tout vert.
# Sinon les AAB sont retirés : aucun AAB non contrôlé ne traîne, prêt à téléverser.
# Sortie : 0 prêts, 1 retirés (étiquette absente), 2 INCONNU (git illisible).
set -uo pipefail
VERSION=${1:?version attendue}
shift
[ $# -gt 0 ] || { echo "❓ INCONNU : aucun AAB nommé" >&2; exit 2; }
ETIQUETTES=$(git tag --points-at HEAD 2>/dev/null) || { echo "❓ INCONNU : étiquettes de HEAD illisibles" >&2; exit 2; }
if ! grep -qx "v$VERSION" <<< "$ETIQUETTES"; then
  rm -f -- "$@"
  echo "❌ HEAD ne porte pas v$VERSION : le contrôle avant soumission n'est pas passé — AAB retirés, rien à envoyer" >&2
  exit 1
fi
echo "✅ v$VERSION posée sur HEAD par le contrôle : AAB prêts à l'envoi"
