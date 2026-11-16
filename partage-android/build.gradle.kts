// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

/**
 * Ce que le téléphone (`:app`) et la montre (`:montre`, #207) partagent et qui demande
 * Android (#206, lot 2) : le chiffrement au Keystore aujourd'hui ; le rendu du texte riche,
 * les palettes, le logo et les libellés du hors ligne aux lots suivants. Le Kotlin pur
 * (client HTTP, formats, alerte reçue) reste dans `:partage`.
 *
 * Une bibliothèque, pas une application : SDK, Java et lint lui viennent du build racine
 * (`reglagesCommuns`), comme aux deux apps. Les pièces gardent leur paquet d'origine
 * (`fr.coprofirst.app.…`) : les déplacer ne renomme aucun appelant.
 */
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "fr.coprofirst.app.partage"
}
