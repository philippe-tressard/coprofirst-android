#!/bin/bash
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
#
# reuse-au-poste.sh — la conformité REUSE au poste, en UNE écriture (#329) : le pre-commit
# et pipeline.sh l'appellent. Sans l'outil, INCONNU (sortie 2), jamais vert ; la CI, elle,
# l'installe à sa version épinglée, celle de scripts/requirements.txt (#339). Une autre
# version au poste jugerait autrement que la CI : INCONNU aussi, avec le geste qui aligne.
set -uo pipefail
cd "$(dirname "$0")/.."
command -v reuse >/dev/null \
  || { echo "❓ reuse absent : conformité REUSE INCONNUE (pipx install reuse)" >&2; exit 2; }
epinglee=$(sed -nE 's/^reuse(\[[^]]*\])?==([0-9.]+).*/\2/p' scripts/requirements.txt)
[ -n "$epinglee" ] \
  || { echo "❓ version de reuse illisible dans scripts/requirements.txt : conformité REUSE INCONNUE" >&2; exit 2; }
installee=$(reuse --version 2>/dev/null | sed -nE 's/.*version ([0-9.]+).*/\1/p' | head -1)
[ "$installee" = "$epinglee" ] \
  || { echo "❓ reuse ${installee:-?} au poste, $epinglee épinglée : conformité REUSE INCONNUE (pipx install --force 'reuse[charset-normalizer]==$epinglee')" >&2; exit 2; }
reuse lint --quiet || { echo "❌ reuse lint" >&2; exit 1; }
