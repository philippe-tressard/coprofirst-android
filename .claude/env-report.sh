#!/bin/bash
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
#
# env-report.sh — l'état VIVANT du projet au démarrage de session. L'étiquette, la
# banque de mémoire et les garde-fous, le hook global les affiche déjà depuis
# ~/.claude/projets.json : ils ne se recopient pas ici (skill nouveau-projet §2).
set -uo pipefail
# Le répertoire attendu se lit au registre — jamais écrit ici : un dépôt public n'a
# pas à publier la disposition du disque du poste.
ATTENDU=$(python3 -c 'import json, os
d = json.load(open(os.path.expanduser("~/.claude/projets.json")))
for p in (d.get("projets", d) if isinstance(d, dict) else d):
    if p.get("nom") == "CoproFirst-Android":
        print(p.get("repertoire", {}).get("macos", ""))' 2>/dev/null)
COURANT=$(pwd)
case "$COURANT" in "$ATTENDU"/.claude/worktrees/*) COURANT=$ATTENDU ;; esac
echo "═══════════════ ENVIRONNEMENT DE TRAVAIL ═══════════════"
echo "  Répertoire    : $COURANT"
echo "  Dépôt/branche : $(git config --get remote.origin.url 2>/dev/null || echo 'aucun dépôt distant') [$(git branch --show-current 2>/dev/null)]"
[ "$(git config --get core.hooksPath 2>/dev/null)" = ".githooks" ] \
  && echo "  Hook pre-commit : armé" \
  || echo "  Hook pre-commit : 🔴 NON ARMÉ — git config core.hooksPath .githooks"
JAVA=$(/usr/libexec/java_home -v 21 2>/dev/null || brew --prefix openjdk@21 2>/dev/null)
[ -x "$JAVA/bin/java" ] || [ -x "$JAVA/libexec/openjdk.jdk/Contents/Home/bin/java" ] \
  && echo "  JDK 21        : présent" \
  || echo "  JDK 21        : 🔴 absent — brew install openjdk@21"
SDK=${ANDROID_HOME:-$(brew --prefix 2>/dev/null)/share/android-commandlinetools}
[ -d "$SDK/platforms" ] \
  && echo "  SDK Android   : $(ls "$SDK/platforms" | sort -V | tail -1)" \
  || echo "  SDK Android   : 🔴 absent — README, « Outillage »"
echo "  Contrat d'API : $(sed -n 's/^| Commit | `\([0-9a-f]\{9\}\).*/coprofirst@\1/p' contrat/PROVENANCE.md 2>/dev/null)"
if [ -z "$ATTENDU" ]; then
  echo "  Répertoire attendu : ❓ INCONNU (registre ~/.claude/projets.json illisible)"
elif [ "$COURANT" != "$ATTENDU" ]; then
  echo "  ⚠️  RÉPERTOIRE INATTENDU (attendu $ATTENDU) — signale-le à l'utilisateur"
  echo "      AVANT d'agir : mémoires et étiquette partiront au mauvais endroit."
fi
echo "  → Reprends cette ligne en tête de ta 1ʳᵉ réponse."
echo "════════════════════════════════════════════════════════"
