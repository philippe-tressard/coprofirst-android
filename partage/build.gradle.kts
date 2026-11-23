// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

/**
 * Ce que le téléphone (`:app`) et la montre Wear OS (`:montre`, #207) partagent, écrit UNE
 * fois (#206) : le client HTTP, les formats du site, l'alerte reçue, le découpage du texte
 * riche du site (`LectureHTML`, que la montre rend comme le téléphone). Du Kotlin pur — ni
 * Android ni ressources — comme `:client-api`, dont l'alerte lit le modèle.
 *
 * Les pièces gardent leur paquet d'origine (`fr.coprofirst.app.…`) : les déplacer ne renomme
 * aucun de leurs appelants. Ce qui demande Android (palettes, logo, libellés du hors ligne,
 * tracés des icônes) vit à côté, dans `:partage-android`.
 */
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin { jvmToolchain(21) }

dependencies {
    // L'alerte reçue se lit depuis le modèle généré : qui prend `:partage` le reçoit.
    api(project(":client-api"))
    api(libs.okhttp)
    testImplementation(libs.junit)
}
