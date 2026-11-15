#!/bin/bash
# SPDX-FileCopyrightText: 2026 Philippe TRESSARD
# SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
#
# importer-contrat.sh — recopie depuis la release de coprofirst au PLANCHER
# (contrat/plancher.env) les pièces jointes dont l'app se génère, et en écrit la
# provenance : openapi.json (le client d'API) et tables-clients.json (la charte).
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
# Les pièces jointes recopiées à l'octet : la seule liste.
PIECES=(openapi.json tables-clients.json)
for piece in "${PIECES[@]}"; do
  gh release download "$PLANCHER" -R "$DEPOT" -p "$piece" -D "$TMP" 2>/dev/null \
    || { echo "❓ INCONNU : $piece de $DEPOT $PLANCHER illisible (gh auth status ?)" >&2; exit 2; }
done
COMMIT=$(gh api "repos/$DEPOT/commits/$PLANCHER" -q .sha 2>/dev/null)
[[ "$COMMIT" =~ ^[0-9a-f]{40}$ ]] || { echo "❓ INCONNU : commit de $PLANCHER illisible" >&2; exit 2; }
CONTRAT=$(python3 -I -c 'import json, sys; print(json.load(open(sys.argv[1]))["info"]["version"])' "$TMP/openapi.json") \
  || { echo "❓ INCONNU : openapi.json sans info.version" >&2; exit 2; }

if [ "${1:-}" = "--verifier" ]; then
  for piece in "${PIECES[@]}"; do
    cmp -s "$TMP/$piece" "contrat/$piece" \
      || { echo "❌ contrat/$piece diffère de $DEPOT $PLANCHER : bash scripts/importer-contrat.sh" >&2; exit 1; }
  done
  grep -q "$COMMIT" contrat/PROVENANCE.md \
    || { echo "❌ contrat/PROVENANCE.md ne cite pas $COMMIT : bash scripts/importer-contrat.sh" >&2; exit 1; }
  echo "✅ ${PIECES[*]} identiques à $DEPOT $PLANCHER (contrat $CONTRAT)"
  exit 0
fi

for piece in "${PIECES[@]}"; do cp "$TMP/$piece" "contrat/$piece"; done
cat > contrat/PROVENANCE.md <<FIN
<!--
SPDX-FileCopyrightText: 2026 Philippe TRESSARD
SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
-->

# Provenance de \`openapi.json\` et \`tables-clients.json\`

GÉNÉRÉ par scripts/importer-contrat.sh — NE PAS MODIFIER À LA MAIN.

| | |
|---|---|
| Source | $DEPOT $PLANCHER, pièces jointes \`openapi.json\` et \`tables-clients.json\` de la release |
| Commit | \`$COMMIT\` (\`$PLANCHER\`) |
| Version du contrat | \`$CONTRAT\` (servie par chaque installation en \`version_contrat\` de \`GET /config\`) |
| Préfixe servi | \`/api\` : les chemins du schéma s'entendent sous \`https://<hôte>/api\` |
| Charte | \`tables-clients.json\` : jetons de la charte et des palettes, d'où \`scripts/generer_charte.py\` tire \`Palette\` au build |
| Normalisation | aucune : chaque fichier est la pièce jointe, à l'octet |
FIN
echo "✅ contrat/{${PIECES[*]}} = $DEPOT $PLANCHER (contrat $CONTRAT)"
