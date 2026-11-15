<!--
SPDX-FileCopyrightText: 2026 Philippe TRESSARD
SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
-->

# Provenance de `openapi.json` et `tables-clients.json`

GÉNÉRÉ par scripts/importer-contrat.sh — NE PAS MODIFIER À LA MAIN.

| | |
|---|---|
| Source | philippe-tressard/coprofirst v3.89.0, pièces jointes `openapi.json` et `tables-clients.json` de la release |
| Commit | `10b85f3356d538254bfdca6f54270063c9008d4b` (`v3.89.0`) |
| Version du contrat | `1.27.0` (servie par chaque installation en `version_contrat` de `GET /config`) |
| Préfixe servi | `/api` : les chemins du schéma s'entendent sous `https://<hôte>/api` |
| Charte | `tables-clients.json` : jetons de la charte et des palettes, d'où `scripts/generer_charte.py` tire `Palette` au build |
| Normalisation | aucune : chaque fichier est la pièce jointe, à l'octet |
