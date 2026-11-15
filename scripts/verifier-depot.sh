#!/bin/bash
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
#
# verifier-depot.sh — les garde-fous du dépôt qui ne demandent ni JDK ni SDK.
# Lancé par la CI, par le hook pre-commit et à la main ; sortie non nulle = refus.
#
#   1. aucun fichier versionné de plus de 500 lignes (standards/02 §6)
#   2. ni BOM ni CRLF (standards/10)
#   3. bit d'exécution sur les scripts, et sur eux seuls (standards/08 §3)
#   4. syntaxe de chaque script bash et Python (standards/05 §6)
#   5. LICENSE = permission magasins + texte de l'AGPL, assemblés par
#      `--ecrire-licence` : LICENSE n'est que la réunion des deux textes de LICENSES/.
#   6. aucune couleur écrite à la main dans le Kotlin de l'app : la charte se génère
#      depuis coprofirst (autotest d'abord : un contrôle qui ne refuse rien ne prouve rien).
#   7. aucun chemin de .claude/worktrees/ suivi.
#
# Il juge les fichiers que git connaît (index compris) : un fichier ignoré n'est
# pas livré, il n'a pas à passer.
set -uo pipefail
cd "$(dirname "$0")/.."

PERMISSION=LICENSES/LicenseRef-CoproFirst-Magasins.txt
AGPL=LICENSES/AGPL-3.0-or-later.txt

assembler_licence() {
  cat "$PERMISSION"
  printf '\n%s\n\n' "================================================================================"
  cat "$AGPL"
}

if [ "${1:-}" = "--ecrire-licence" ]; then
  assembler_licence > LICENSE
  echo "✅ LICENSE réassemblé depuis LICENSES/"
  exit 0
fi

ECHECS=0
echec() { echo "❌ $*"; ECHECS=$((ECHECS + 1)); }

FICHIERS=$(git ls-files --cached)
[ -n "$FICHIERS" ] || { echo "❓ INCONNU : git ne connaît aucun fichier (rien d'indexé ?)"; exit 2; }

# Dispensés de la limite de longueur : produits par un outil ou textes de licence,
# jamais écrits à la main. Toute autre exception se discute, elle ne s'ajoute pas.
DISPENSES_LONGUEUR='^(LICENSE|LICENSES/.*|contrat/openapi\.json)$'

while IFS= read -r f; do
  [ -f "$f" ] || continue
  grep -Iq . "$f" 2>/dev/null || continue   # un binaire n'a ni lignes ni fins de ligne
  if ! [[ "$f" =~ $DISPENSES_LONGUEUR ]]; then
    n=$(wc -l < "$f" | tr -d ' ')
    [ "$n" -le 500 ] || echec "$f : $n lignes (> 500) — découper (standards/02 §6)"
  fi
  [ "$(head -c 3 "$f" | od -An -tx1 | tr -d " \n")" != "efbbbf" ] || echec "$f : BOM UTF-8"
  ! grep -q $'\r' "$f" || echec "$f : fins de ligne CRLF"
done <<< "$FICHIERS"

# `git ls-files --stage` : « <mode> <objet> <étape>\t<chemin> ».
while IFS=$'\t' read -r meta f; do
  mode=${meta%% *}
  if [[ "$f" == *.sh || "$f" == .githooks/* || "$f" == gradlew ]]; then
    [ "$mode" = "100755" ] || echec "$f : mode $mode, attendu 100755 (git update-index --chmod=+x)"
    bash -n "$f" 2>/dev/null || echec "$f : syntaxe bash invalide"
  elif [[ "$f" == *.py ]]; then
    if head -1 "$f" | grep -q '^#!'; then
      [ "$mode" = "100755" ] || echec "$f : shebang sans bit d'exécution (git update-index --chmod=+x)"
    fi
    python3 -c 'import ast, sys; ast.parse(open(sys.argv[1], encoding="utf-8").read())' "$f" 2>/dev/null \
      || echec "$f : syntaxe Python invalide"
  elif [ "$mode" = "100755" ]; then
    echec "$f : exécutable sans être un script (git update-index --chmod=-x)"
  fi
done < <(git ls-files --stage)

[ -f LICENSE ] && cmp -s LICENSE <(assembler_licence) \
  || echec "LICENSE diverge de LICENSES/ : bash scripts/verifier-depot.sh --ecrire-licence"

# Les couleurs se GÉNÈRENT depuis les sources de coprofirst : aucune écrite à la main.
COULEURS_MAIN='Color\(0x|Color\([0-9]|Color\.(Red|Blue|Green|Yellow|Cyan|Magenta|Black|White|Gray|DarkGray|LightGray)\b|#[0-9A-Fa-f]{6,8}\b'
refus_couleur() { grep -noE "$COULEURS_MAIN" "$1" | head -1; }
autotest_couleurs() {
  local d ok=0
  d=$(mktemp -d) || return 1
  printf 'val c = Color(0xFF112233)\n' > "$d/hexa"
  printf 'val c = Color.Red\n' > "$d/nommee"
  printf '<color name="x">#112233</color>\n' > "$d/xml"
  printf 'val c = MaterialTheme.colorScheme.primary\n' > "$d/propre"
  [ -n "$(refus_couleur "$d/hexa")" ] || ok=1
  [ -n "$(refus_couleur "$d/nommee")" ] || ok=1
  [ -n "$(refus_couleur "$d/xml")" ] || ok=1
  [ -z "$(refus_couleur "$d/propre")" ] || ok=1
  rm -rf "${d:?}"
  return $ok
}
autotest_couleurs || echec "verifier-depot.sh : autotest des couleurs en échec"
while IFS= read -r f; do
  [ -f "$f" ] || continue
  motif=$(refus_couleur "$f")
  [ -z "$motif" ] || echec "$f : couleur écrite à la main ($motif) — à générer depuis coprofirst"
done < <(grep -E '^app/src/main/.*\.(kt|xml)$' <<< "$FICHIERS")

! grep -q '^\.claude/worktrees/' <<< "$FICHIERS" || echec ".claude/worktrees/ suivi : git rm -r --cached .claude/worktrees"

if [ "$ECHECS" -gt 0 ]; then
  echo "── $ECHECS échec(s)"
  exit 1
fi
echo "✅ verifier-depot.sh : dépôt conforme"
