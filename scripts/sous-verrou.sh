#!/bin/bash
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
#
# sous-verrou.sh <libellé> -- <commande…> — lance une TÂCHE LOURDE du poste (Gradle,
# émulateur) sous le verrou commun du poste (`~/.claude/bin/verrou_poste.py`,
# claude-config#552, #55) : une seule à la fois sur le Mac, dans l'ordre d'arrivée. Le
# verrou DEVIENT la commande (son code est rendu ; la tuer relâche le verrou).
#
# - Déjà sous le verrou (`VERROU_POSTE_TENU`) : la commande tourne telle quelle.
# - Sans le verrou commun (la CI, un autre poste) : la commande tourne telle quelle, et il
#   le dit.
# `VERROU_POSTE` remplace le chemin du verrou (les tests).
set -uo pipefail
libelle=${1:?libellé attendu}
shift
[ "${1:-}" = "--" ] && shift
VERROU=${VERROU_POSTE:-$HOME/.claude/bin/verrou_poste.py}
if [ -n "${VERROU_POSTE_TENU:-}" ]; then
  exec "$@"
fi
if [ ! -f "$VERROU" ]; then
  echo "ℹ️  sans verrou commun du poste ($VERROU absent) : « $libelle » tourne seul" >&2
  exec "$@"
fi
export VERROU_POSTE_TENU=1
exec python3 "$VERROU" prendre --libelle "$libelle" --depot "$PWD" -- "$@"
