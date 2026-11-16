// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

/** Le décalage du code de version de la montre sur celui du téléphone (voir `versionCode`). */
val DECALAGE_MONTRE = 1_000_000_000

/**
 * La montre Wear OS (#207) : la cloche de la dernière résidence ouverte sur le téléphone,
 * en lecture seule — pendant de l'Apple Watch (coprofirst-ios#226). C'est la MÊME app
 * Google Play que le téléphone : même identifiant, même release, même clé (build racine).
 */
android {
    namespace = "fr.coprofirst.montre"

    defaultConfig {
        // Wear OS 3 et plus : les montres d'avant n'ont plus de magasin.
        minSdk = 30
        // Google Play exige un code UNIQUE par artefact d'une même app, et sert à un appareil
        // le plus haut qui lui convient : la montre se place au-dessus du téléphone, du même
        // nombre de commits (build racine). Le décalage ne bouge jamais.
        versionCode = versionCode?.plus(DECALAGE_MONTRE)
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    lint {
        // L'icône vient du logo, généré au build par la couche commune (#206) ; la montre la
        // reçoit avec la complication (#207, lot 5). Rien n'est publié d'ici là.
        disable += "MissingApplicationIcon"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.wear.compose.material3)
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.compose.navigation)
    // Le client HTTP, les formats du site, l'alerte reçue : ceux du téléphone (#206).
    implementation(project(":partage"))
    testImplementation(libs.junit)
    // Le geste commun des tests d'interface (tests-interface-communs, #216) se compile ici aussi.
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
    debugImplementation(libs.compose.ui.test.manifest)
}
