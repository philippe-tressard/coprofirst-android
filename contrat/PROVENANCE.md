<!--
SPDX-FileCopyrightText: 2026 Philippe TRESSARD
SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
-->

# Provenance de `openapi.json`, `tables-clients.json` et `constantes-clients.json`

GÉNÉRÉ par scripts/importer-contrat.sh — NE PAS MODIFIER À LA MAIN.

| | |
|---|---|
| Source | philippe-tressard/coprofirst v3.103.0, pièces jointes `openapi.json`, `tables-clients.json` et `constantes-clients.json` de la release |
| Commit | `1d1ed30cb3fa2128692ce2be0fe34ded87d72c69` (`v3.103.0`) |
| Version du contrat | `2.0.0` (servie par chaque installation en `version_contrat` de `GET /config`) |
| Préfixe servi | `/api` : les chemins du schéma s'entendent sous `https://<hôte>/api` |
| Charte | `tables-clients.json` : jetons de la charte et des palettes, d'où `scripts/generer_charte.py` tire `Palette` au build |
| Constantes | `constantes-clients.json` : ce que le serveur applique sans le dire dans le contrat (longueur du mot de passe, schémas de lien…) |
| Normalisation | aucune : chaque fichier est la pièce jointe, à l'octet |
