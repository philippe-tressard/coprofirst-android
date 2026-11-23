#!/bin/bash
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
#
# construire-release.sh — les AAB (téléphone, montre #207) signés par la clé de TÉLÉVERSEMENT, prêts pour la Play
# Console (Play App Signing : Google re-signe avec la clé de l'app). Au poste
# seulement, jamais en CI (#33) ; il ne téléverse rien : la publication est un geste
# de Philippe (docs/signature-android.md).
#
# Il refuse de construire ce qui ne serait pas `main` tel que publié : une branche,
# un arbre modifié, ou un `main` local décalé d'origin. La version se déduit au build
# (versionCode = nombre de commits, build racine ; la montre se place au-dessus).
#   --verifier : contrôle les prérequis (clé, mot de passe, arbre) sans construire.
# Un prérequis illisible rend INCONNU (sortie 2), jamais OK.
set -uo pipefail
cd "$(dirname "$0")/.."
# Une tâche lourde du poste : sous le verrou commun (#55), elle attend son tour ;
# `--verifier` ne construit rien, il ne le prend pas.
if [ -z "${VERROU_POSTE_TENU:-}" ] && [ "${1:-}" != "--verifier" ]; then
  exec bash scripts/sous-verrou.sh "release Android (bundleRelease)" -- bash scripts/construire-release.sh "$@"
fi

CLE="${COPROFIRST_CLE_TELEVERSEMENT:-$HOME/.coprofirst-android/televersement.jks}"
SERVICE=coprofirst-android-televersement

echec() { echo "❌ $1" >&2; exit 1; }
inconnu() { echo "❓ INCONNU : $1" >&2; exit 2; }

[ -f "$CLE" ] || echec "clé de téléversement absente : $CLE (docs/signature-android.md)"
MODE=$(stat -f '%Lp' "$CLE" 2>/dev/null || stat -c '%a' "$CLE" 2>/dev/null) || inconnu "droits de $CLE illisibles"
[ "$MODE" = 600 ] || echec "$CLE est en $MODE : chmod 600 (lisible par son seul propriétaire)"
command -v security >/dev/null || inconnu "le trousseau macOS (security) est introuvable"
MDP=$(security find-generic-password -s "$SERVICE" -a "$USER" -w 2>/dev/null) \
  || echec "mot de passe absent du trousseau (service $SERVICE) : docs/signature-android.md"

git fetch -q origin main || inconnu "origin/main illisible"
[ "$(git rev-parse --abbrev-ref HEAD)" = main ] || echec "pas sur main"
[ -z "$(git status --porcelain)" ] || echec "arbre modifié : la release se construit d'un main propre"
[ "$(git rev-parse HEAD)" = "$(git rev-parse origin/main)" ] || echec "main local ≠ origin/main : git pull --ff-only"

if [ "${1:-}" = "--verifier" ]; then
  echo "✅ prérequis réunis : clé $CLE (600), mot de passe au trousseau, main = origin/main"
  exit 0
fi

# Sans cache de configuration : le mot de passe n'est écrit nulle part sur le disque.
COPROFIRST_SIGNATURE_MDP="$MDP" ./gradlew -q --no-configuration-cache --max-workers=2 \
  -Pcoprofirst.signature.cle="$CLE" :app:afficherVersion :app:bundleRelease :montre:afficherVersion :montre:bundleRelease \
  || echec "build de la release"
unset MDP

# Les permissions du manifeste FUSIONNÉ (téléphone, montre) : celles que la déclaration de la
# section « Sécurité des données » nomme, et elles seules (#327). Une bibliothèque montée de
# version peut en ajouter une sans toucher au dépôt.
# Le manifeste jugé est celui de CE build : sa version (base + nombre de commits, build racine)
# est exigée ; un manifeste d'un autre build, ou illisible, rend INCONNU.
VERSION="$(sed -n 's/^coprofirst\.version\.base=//p' gradle.properties).$(git rev-list --count HEAD)"
python3 -I scripts/verifier_manifeste_fusionne.py --version "$VERSION"
case $? in
  0) ;;
  1) echec "permission du manifeste fusionné absente de docs/play-store/securite-des-donnees.md (#327)" ;;
  *) inconnu "manifeste fusionné de $VERSION illisible (#327)" ;;
esac

# Le téléphone et la montre (#207) : une même app Google Play, deux artefacts, une clé.
for AAB in app/build/outputs/bundle/release/app-release.aab montre/build/outputs/bundle/release/montre-release.aab; do
  [ -f "$AAB" ] || inconnu "$AAB introuvable après le build"
  jarsigner -verify "$AAB" >/dev/null 2>&1 || echec "$AAB n'est pas signé"
  echo "✅ $AAB"
  echo "   empreinte SHA-256 du certificat de téléversement (à comparer à la Play Console) :"
  # La sortie de keytool suit la langue du poste (« SHA 256: » en français) : forcée en
  # anglais pour être lue ; une empreinte introuvable rend INCONNU, jamais une ligne vide.
  EMPREINTE=$(keytool -J-Duser.language=en -printcert -jarfile "$AAB" 2>/dev/null | grep -E 'SHA256:' | head -1)
  [ -n "$EMPREINTE" ] || inconnu "empreinte du certificat de $AAB illisible (keytool)"
  echo "$EMPREINTE" | sed 's/^[[:space:]]*/   /'
  shasum -a 256 "$AAB" | sed 's/^/   fichier : /'
done
