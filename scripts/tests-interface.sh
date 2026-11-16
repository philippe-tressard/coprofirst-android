#!/bin/bash
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
#
# tests-interface.sh — les tests d'interface au poste, sur l'émulateur (AVD
# « coprofirst »). La CI les joue aussi (job « Interface ») : c'est elle qui fait foi.
#
# - Une seule tâche lourde au poste à la fois : une suite xcodebuild ou un autre
#   émulateur en cours → on n'ouvre pas le nôtre (sortie 3), on attend leur fin.
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
# Les émulateurs par leur EXÉCUTABLE (ps comm), pas par le texte des commandes : `pgrep -f`
# trouvait aussi la ligne de commande de qui lance ce script, si elle nommait l'émulateur.
emulateurs() { ps -axo comm=,args= | awk '$1 ~ /qemu-system/'; }
if [ -n "$(emulateurs)" ] && ! emulateurs | grep -q -- '-avd coprofirst'; then
  echo "⏸  un autre émulateur tourne : une seule tâche lourde au poste — relancer à sa fin" >&2
  exit 3
fi
a() { for _ in $(seq 1 15); do [ "$("$ADB" get-state 2>/dev/null)" = device ] && "$ADB" "$@" 2>/dev/null && return 0; sleep 2; done; return 1; }

DEMARRE=0
if ! emulateurs | grep -q -- '-avd coprofirst'; then
  nohup "$SDK/emulator/emulator" -avd coprofirst -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect >/dev/null 2>&1 &
  DEMARRE=1
fi
arreter() { [ "$DEMARRE" = 1 ] && a emu kill >/dev/null; }
trap arreter EXIT
for _ in $(seq 1 90); do [ "$(a shell getprop sys.boot_completed | tr -d '\r')" = 1 ] && break; sleep 3; done

# Le démon partagé du poste (gradle.properties) : jamais --no-daemon ici.
./gradlew -q :app:assembleDebug :app:assembleDebugAndroidTest || exit 1
# `-d` : la version se déduit du nombre de commits ; une branche plus courte que la dernière
# build installée rétrogradait, et l'installation était refusée (en silence : la raison
# d'adb est désormais dite).
installer() {
  local sortie
  sortie=$(a install -r -d "$1") || { echo "❓ INCONNU : installation impossible ($1) — $sortie" >&2; exit 2; }
}
installer app/build/outputs/apk/debug/app-debug.apk
installer app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
FILTRE=${1:+-e class $1}
a shell "rm -f /data/local/tmp/resultat.txt; nohup am instrument -w -r $FILTRE fr.coprofirst.app.test/androidx.test.runner.AndroidJUnitRunner > /data/local/tmp/resultat.txt 2>&1 &"
for _ in $(seq 1 200); do
  sleep 3
  a shell cat /data/local/tmp/resultat.txt 2>/dev/null | grep -q 'INSTRUMENTATION_CODE' && break
done
RESULTAT=$(a shell cat /data/local/tmp/resultat.txt 2>/dev/null)
# Le résultat ENTIER reste au poste (l'émulateur s'éteint et l'emporte) : le nom de chaque
# test en échec et sa pile s'y lisent ; ici, le test en échec puis son message.
mkdir -p app/build/outputs && printf '%s\n' "$RESULTAT" > app/build/outputs/tests-interface-resultat.txt
awk '/INSTRUMENTATION_STATUS: class=/{c=$0} /INSTRUMENTATION_STATUS: test=/{t=$0} /INSTRUMENTATION_STATUS: stack=/{sub(/.*class=/,"",c); sub(/.*test=/,"",t); print "✗ " c "." t; print "  " $0}' <<< "$RESULTAT" | sed 's/INSTRUMENTATION_STATUS: stack=//'
grep -E 'FAILURES|^OK \(|Tests run' <<< "$RESULTAT"
echo "Résultat entier : app/build/outputs/tests-interface-resultat.txt"
if grep -q '^OK (' <<< "$RESULTAT"; then exit 0; fi
if grep -q 'FAILURES' <<< "$RESULTAT"; then exit 1; fi
echo "❓ INCONNU : aucun résultat lu (instrumentation interrompue ?)" >&2
exit 2
