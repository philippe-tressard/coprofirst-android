<!--
SPDX-FileCopyrightText: 2026 Philippe TRESSARD
SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
-->

# Provenance des pièces jointes de la release

GÉNÉRÉ par scripts/importer-contrat.sh — NE PAS MODIFIER À LA MAIN.

| | |
|---|---|
| Source | philippe-tressard/coprofirst v3.117.0, pièces jointes `openapi.json tables-clients.json constantes-clients.json icones-svg.json` et les textes `ISC MIT CC0-1.0` de la release |
| Commit | `d25fa8df3721cf67b5522648fd22d000b61ca237` (`v3.117.0`) |
| Version du contrat | `2.8.0` (servie par chaque installation en `version_contrat` de `GET /config`) |
| Préfixe servi | `/api` : les chemins du schéma s'entendent sous `https://<hôte>/api` |
| Charte | `tables-clients.json` : jetons de la charte et des palettes, d'où `scripts/generer_charte.py` tire `Palette` au build |
| Constantes | `constantes-clients.json` : ce que le serveur applique sans le dire dans le contrat (longueur du mot de passe, schémas de lien…) |
| Icônes | `icones-svg.json` : les tracés Lucide du site (ISC, MIT pour la part issue de Feather), le logo WhatsApp de Simple Icons (CC0-1.0) ; leurs textes dans `LICENSES/` |
| Au-dessus du plancher | `au-dessus/openapi.json` : la pièce de v3.118.0 ; seuls ses chemins `/masquages /masquages/{auteur_id}` (et leurs schémas) sont greffés au client, derrière une porte de version |
| Normalisation | aucune : chaque fichier est la pièce jointe, à l'octet |
