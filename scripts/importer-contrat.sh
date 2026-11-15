#!/bin/bash
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
#
# importer-contrat.sh — recopie contrat/openapi.json depuis la pièce jointe de la
# release de coprofirst au PLANCHER (contrat/plancher.env), et en écrit la provenance.
#   --verifier : n'écrit rien ; échoue si le contrat versionné diffère de la release
#                (la CI le rejoue : rien de généré ne se retouche à la main).
# Une release illisible rend INCONNU (sortie 2), jamais OK.
set -uo pipefail
cd "$(dirname "$0")/.."
DEPOT=philippe-tressard/coprofirst
source contrat/plancher.env 2>/dev/null
[[ "${PLANCHER:-}" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]] \
  || { echo "❓ INCONNU : plancher illisible (contrat/plancher.env)" >&2; exit 2; }

TMP=$(mktemp -d) || exit 2
trap 'rm -rf "${TMP:?}"' EXIT
gh release download "$PLANCHER" -R "$DEPOT" -p openapi.json -D "$TMP" 2>/dev/null \
  || { echo "❓ INCONNU : openapi.json de $DEPOT $PLANCHER illisible (gh auth status ?)" >&2; exit 2; }
COMMIT=$(gh api "repos/$DEPOT/commits/$PLANCHER" -q .sha 2>/dev/null)
[[ "$COMMIT" =~ ^[0-9a-f]{40}$ ]] || { echo "❓ INCONNU : commit de $PLANCHER illisible" >&2; exit 2; }
CONTRAT=$(python3 -I -c 'import json, sys; print(json.load(open(sys.argv[1]))["info"]["version"])' "$TMP/openapi.json") \
  || { echo "❓ INCONNU : openapi.json sans info.version" >&2; exit 2; }

if [ "${1:-}" = "--verifier" ]; then
  cmp -s "$TMP/openapi.json" contrat/openapi.json \
    || { echo "❌ contrat/openapi.json diffère de $DEPOT $PLANCHER : bash scripts/importer-contrat.sh" >&2; exit 1; }
  grep -q "$COMMIT" contrat/PROVENANCE.md \
    || { echo "❌ contrat/PROVENANCE.md ne cite pas $COMMIT : bash scripts/importer-contrat.sh" >&2; exit 1; }
  echo "✅ contrat identique à $DEPOT $PLANCHER (contrat $CONTRAT)"
  exit 0
fi

cp "$TMP/openapi.json" contrat/openapi.json
cat > contrat/PROVENANCE.md <<FIN
<!--
SPDX-FileCopyrightText: 2026 Philippe TRESSARD
SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
-->

# Provenance de \`openapi.json\`

GÉNÉRÉ par scripts/importer-contrat.sh — NE PAS MODIFIER À LA MAIN.

| | |
|---|---|
| Source | $DEPOT $PLANCHER, pièce jointe \`openapi.json\` de la release |
| Commit | \`$COMMIT\` (\`$PLANCHER\`) |
| Version du contrat | \`$CONTRAT\` (servie par chaque installation en \`version_contrat\` de \`GET /config\`) |
| Préfixe servi | \`/api\` : les chemins du schéma s'entendent sous \`https://<hôte>/api\` |
| Normalisation | aucune : le fichier est la pièce jointe, à l'octet |
FIN
echo "✅ contrat/openapi.json = $DEPOT $PLANCHER (contrat $CONTRAT)"
