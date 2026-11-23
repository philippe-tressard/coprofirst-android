#!/bin/bash
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
#
# pret-a-envoyer.sh <sortie du contrôle> <version> <aab>… — la dernière porte de
# construire-release.sh (#322). Un AAB ne reste à envoyer que si le contrôle avant soumission
# (`controle_avant_soumission.py --taguer`, #4) est sorti VERT et que le commit construit porte
# l'étiquette `v<version>` qu'il pose alors. Dans tout autre cas — rouge, INCONNU (réseau,
# `gh` : un contrôle qui n'a pas pu juger n'a pas jugé), étiquette absente — les AAB sont
# retirés : aucun AAB non contrôlé ne reste prêt à téléverser ; on relance la release une fois
# la cause levée.
# Sortie : 0 prêts ; 1 retirés (rouge, ou étiquette absente) ; 2 retirés (INCONNU).
set -uo pipefail
CONTROLE=${1:?sortie du contrôle attendue}
VERSION=${2:?version attendue}
shift 2
[ $# -gt 0 ] || { echo "❓ INCONNU : aucun AAB nommé" >&2; exit 2; }
retirer() { rm -f -- "$@"; }
if [ "$CONTROLE" != 0 ]; then
  retirer "$@"
  echo "❌ contrôle avant soumission en sortie $CONTROLE (build/controle-soumission.md) : AAB retirés, rien à envoyer" >&2
  [ "$CONTROLE" = 1 ] && exit 1
  exit 2
fi
ETIQUETTES=$(git tag --points-at HEAD 2>/dev/null) || { retirer "$@"; echo "❓ INCONNU : étiquettes de HEAD illisibles — AAB retirés" >&2; exit 2; }
if ! grep -qx "v$VERSION" <<< "$ETIQUETTES"; then
  retirer "$@"
  echo "❌ contrôle vert, mais HEAD ne porte pas v$VERSION : AAB retirés, rien à envoyer" >&2
  exit 1
fi
echo "✅ contrôle vert et v$VERSION posée sur HEAD : AAB prêts à l'envoi"
