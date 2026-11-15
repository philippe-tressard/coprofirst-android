<!--
SPDX-FileCopyrightText: 2026 Philippe TRESSARD
SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
-->
── PRÉ-FLIGHT CoproFirst-Android — avant CETTE action → charger CECI ──────
  • un écran Compose, un parcours, un libellé  → standards/11 ; l'écran iOS
    correspondant (coprofirst-ios) est la référence de parcours, pas du code à recopier
  • un appel à l'API                            → le client GÉNÉRÉ (client-api/) avec
    session/ClientHttp.kt ; une route absente = un ticket dans coprofirst
  • une couleur, une taille, un format          → généré depuis coprofirst, jamais recopié
  • une dépendance, une image, une police       → REUSE.toml, licence compatible (standards/14 §6)
  • le logo de CoproFirst                       → LicenseRef-CoproFirst-Logo : recopié à
    l'identique (scripts/importer-logo.sh), ni redessiné ni recoloré
  • une notification, un SDK, une collecte, un droit → standards/14 §1 et §3 bis ;
    la section « Sécurité des données » de Google Play suit le code
  • signature, Play Console, fiche du magasin   → rien sans demande explicite de Philippe
────────────────────────────────────────────────────────────────────────────
