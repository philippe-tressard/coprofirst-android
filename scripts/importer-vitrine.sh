#!/bin/bash
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
#
# importer-vitrine.sh [--verifier] — le contenu de la vitrine (#10) : site/contenu.json
# de coprofirst-site, recopié À L'OCTET au commit COMMIT, dans les ressources de l'app
# (« En savoir plus », la démo). Les textes sont ceux du site, jamais réécrits ici.
#   --verifier : n'écrit rien ; échoue si la copie diffère (la CI le rejoue).
# Le commit ne monte que par décision : on change COMMIT (un commit de la branche
# principale de coprofirst-site), on relance ce script, et PagesVitrineTest dit si la
# copie porte encore chaque id dont l'app dépend. Source illisible : INCONNU (sortie 2).
set -uo pipefail
cd "$(dirname "$0")/.."
DEPOT=philippe-tressard/coprofirst-site
# Le contenu servi le 16/11/2026, hôtes d'exemple sur des noms réservés (coprofirst-site#96).
COMMIT=84d0e76bcb3b20acff09385ebc1cf6b0522594f1
DISTANT=site/contenu.json
LOCAL=app/src/main/assets/vitrine/contenu.json
TMP=$(mktemp -d) || exit 2
trap 'rm -rf "${TMP:?}"' EXIT
gh api "repos/$DEPOT/contents/$DISTANT?ref=$COMMIT" -q .content 2>/dev/null | base64 -d > "$TMP/f" && [ -s "$TMP/f" ] \
  || { echo "❓ INCONNU : $DISTANT illisible sur coprofirst-site@$COMMIT" >&2; exit 2; }
if [ "${1:-}" = "--verifier" ]; then
  cmp -s "$TMP/f" "$LOCAL" || { echo "❌ $LOCAL diffère de coprofirst-site@$COMMIT:$DISTANT — bash scripts/importer-vitrine.sh" >&2; exit 1; }
else
  cp "$TMP/f" "$LOCAL"
fi
echo "✅ contenu de la vitrine identique à coprofirst-site@${COMMIT:0:7}"
