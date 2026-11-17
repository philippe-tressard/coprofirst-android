#!/bin/bash
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
#
# importer-logo.sh [--verifier] — le logo de CoproFirst et sa licence, recopiés À
# L'IDENTIQUE depuis coprofirst à l'étiquette LOGO de contrat/plancher.env : la
# première version publiée dont la licence couvre l'app Android (coprofirst#2743).
#   --verifier : n'écrit rien ; échoue si l'une des copies diffère (la CI le rejoue).
# Le logo n'est pas sous l'AGPL : LicenseRef-CoproFirst-Logo. Le format d'Android
# (VectorDrawable) en est GÉNÉRÉ au build (scripts/generer_logo.py), sans modifier
# ni le dessin ni les couleurs. Source illisible : INCONNU (sortie 2), jamais OK.
set -uo pipefail
cd "$(dirname "$0")/.."
DEPOT=philippe-tressard/coprofirst
source contrat/plancher.env 2>/dev/null
[[ "${LOGO:-}" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "❓ INCONNU : LOGO illisible (contrat/plancher.env)" >&2; exit 2; }
# chemin local → chemin dans coprofirst : la seule liste.
COPIES=("partage-android/src/main/logo/logo.svg:front/static/favicon.svg" "LICENSES/LicenseRef-CoproFirst-Logo.txt:LICENSES/LicenseRef-CoproFirst-Logo.txt")
TMP=$(mktemp -d) || exit 2
trap 'rm -rf "${TMP:?}"' EXIT
ECHECS=0
for copie in "${COPIES[@]}"; do
  local_=${copie%%:*}; distant=${copie#*:}
  gh api "repos/$DEPOT/contents/$distant?ref=$LOGO" -q .content 2>/dev/null | base64 -d > "$TMP/f" && [ -s "$TMP/f" ] \
    || { echo "❓ INCONNU : $distant illisible sur coprofirst@$LOGO" >&2; exit 2; }
  if [ "${1:-}" = "--verifier" ]; then
    cmp -s "$TMP/f" "$local_" || { echo "❌ $local_ diffère de coprofirst@$LOGO:$distant — bash scripts/importer-logo.sh" >&2; ECHECS=1; }
  else
    cp "$TMP/f" "$local_"
  fi
done
[ "$ECHECS" = 0 ] || exit 1
echo "✅ logo et licence identiques à coprofirst@$LOGO"
