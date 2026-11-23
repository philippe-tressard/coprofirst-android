<!--
SPDX-FileCopyrightText: 2026 Philippe TRESSARD
SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
-->

# Provenance des pièces jointes de la release

GÉNÉRÉ par scripts/importer-contrat.sh — NE PAS MODIFIER À LA MAIN.

| | |
|---|---|
| Source | philippe-tressard/coprofirst v3.121.2, pièces jointes `openapi.json tables-clients.json constantes-clients.json icones-svg.json` et les textes `ISC MIT CC0-1.0` de la release |
| Commit | `ac4f80f6ad5c8e861e61bd892c27d129025f80fa` (`v3.121.2`) |
| Version du contrat | `2.10.0` (servie par chaque installation en `version_contrat` de `GET /config`) |
| Préfixe servi | `/api` : les chemins du schéma s'entendent sous `https://<hôte>/api` |
| Charte | `tables-clients.json` : jetons de la charte et des palettes, d'où `scripts/generer_charte.py` tire `Palette` au build |
| Constantes | `constantes-clients.json` : ce que le serveur applique sans le dire dans le contrat (longueur du mot de passe, schémas de lien…) |
| Icônes | `icones-svg.json` : les tracés Lucide du site (ISC, MIT pour la part issue de Feather), le logo WhatsApp de Simple Icons (CC0-1.0) ; leurs textes dans `LICENSES/` |
| Au-dessus du plancher | aucune greffe : `AU_DESSUS` et `GREFFES` vides (`plancher.env`) |
| Normalisation | aucune : chaque fichier est la pièce jointe, à l'octet |
