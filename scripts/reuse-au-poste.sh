#!/bin/bash
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
#
# reuse-au-poste.sh — la conformité REUSE au poste, en UNE écriture (#329) : le pre-commit
# et pipeline.sh l'appellent. Sans l'outil, INCONNU (sortie 2), jamais vert ; la CI, elle,
# l'installe à sa version épinglée (ci.yml, job « Dépôt »).
set -uo pipefail
cd "$(dirname "$0")/.."
command -v reuse >/dev/null \
  || { echo "❓ reuse absent : conformité REUSE INCONNUE (pipx install reuse)" >&2; exit 2; }
reuse lint --quiet || { echo "❌ reuse lint" >&2; exit 1; }
