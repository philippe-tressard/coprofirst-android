#!/bin/bash
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
#
# tests-interface.sh — les tests d'interface au poste, sur l'émulateur (AVD
# « coprofirst »). La CI les joue aussi (job « Interface ») : c'est elle qui fait foi.
#
# - Une seule tâche lourde au poste à la fois : une suite xcodebuild en cours → on
#   n'ouvre pas l'émulateur (sortie 3), on attend sa fin.
# - L'instrumentation tourne DÉTACHÉE sur l'appareil : l'adb du poste redémarre
#   parfois, et un `am instrument` attaché mourrait avec lui (constaté le 15/11/2026).
# - L'émulateur s'arrête à la fin s'il a été démarré ici.
# - Aucun résultat lu : INCONNU (sortie 2), jamais OK.
#   $1 (facultatif) : une classe ou une méthode (`-e class`).
set -uo pipefail
cd "$(dirname "$0")/.."
SDK=${ANDROID_HOME:-$(brew --prefix 2>/dev/null)/share/android-commandlinetools}
ADB=$SDK/platform-tools/adb
export ANDROID_HOME=$SDK
export JAVA_HOME=${JAVA_HOME:-$(brew --prefix openjdk@21 2>/dev/null)/libexec/openjdk.jdk/Contents/Home}

if pgrep -x xcodebuild >/dev/null; then
  echo "⏸  une suite iOS (xcodebuild) tourne : une seule tâche lourde au poste — relancer à sa fin" >&2
  exit 3
fi
a() { for _ in $(seq 1 15); do [ "$("$ADB" get-state 2>/dev/null)" = device ] && "$ADB" "$@" 2>/dev/null && return 0; sleep 2; done; return 1; }

DEMARRE=0
if ! pgrep -f 'qemu-system.*coprofirst' >/dev/null; then
  nohup "$SDK/emulator/emulator" -avd coprofirst -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect >/dev/null 2>&1 &
  DEMARRE=1
fi
arreter() { [ "$DEMARRE" = 1 ] && a emu kill >/dev/null; }
trap arreter EXIT
for _ in $(seq 1 90); do [ "$(a shell getprop sys.boot_completed | tr -d '\r')" = 1 ] && break; sleep 3; done

./gradlew --no-daemon -q :app:assembleDebug :app:assembleDebugAndroidTest || exit 1
a install -r app/build/outputs/apk/debug/app-debug.apk >/dev/null || { echo "❓ INCONNU : installation impossible" >&2; exit 2; }
a install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk >/dev/null || { echo "❓ INCONNU : installation impossible" >&2; exit 2; }
FILTRE=${1:+-e class $1}
a shell "rm -f /data/local/tmp/resultat.txt; nohup am instrument -w -r $FILTRE fr.coprofirst.app.test/androidx.test.runner.AndroidJUnitRunner > /data/local/tmp/resultat.txt 2>&1 &"
for _ in $(seq 1 200); do
  sleep 3
  a shell cat /data/local/tmp/resultat.txt 2>/dev/null | grep -q 'INSTRUMENTATION_CODE' && break
done
RESULTAT=$(a shell cat /data/local/tmp/resultat.txt 2>/dev/null)
grep -E 'AssertionError|Failed to assert|FAILURES|^OK \(|Tests run' <<< "$RESULTAT"
if grep -q '^OK (' <<< "$RESULTAT"; then exit 0; fi
if grep -q 'FAILURES' <<< "$RESULTAT"; then exit 1; fi
echo "❓ INCONNU : aucun résultat lu (instrumentation interrompue ?)" >&2
exit 2
