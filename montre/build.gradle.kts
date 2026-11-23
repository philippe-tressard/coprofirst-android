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
        // Les fichiers que les tests d'interface écrivent (l'aperçu de la tuile) remontent de
        // l'émulateur dans build/outputs/connected_android_test_additional_output.
        testInstrumentationRunnerArguments["useTestStorageService"] = "true"
    }
}

// Les composants tiers que la montre embarque (#317) : la déclaration commune, confrontée à SA
// variante publiée — le build s'arrête sur un module non déclaré —, et ses textes de licence
// copiés dans ses ressources, avec chaque copie. Ils se lisent dans « À propos » du téléphone
// (même app Google Play, même déclaration) : la montre n'a pas d'écran de licences, comme
// l'Apple Watch (écran rond ; buildSrc, ComposantsTiers.kt).
val genererComposantsTiers = composantsTiers(autreApp = ":app")
androidComponents {
    onVariants { variante ->
        variante.sources.assets?.addGeneratedSourceDirectory(genererComposantsTiers, GenererDepuisPython::sortie)
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
    // La tuile (#207, lot 4) : Tiles et ProtoLayout (AndroidX, Apache-2.0), et le futur
    // qu'attend TileService, sans Guava.
    implementation(libs.wear.tiles)
    implementation(libs.wear.protolayout.material3)
    // La complication du cadran (#207, lot 5) : AndroidX, Apache-2.0.
    implementation(libs.wear.complications.data.source)
    implementation(libs.androidx.concurrent.futures)
    // « Continuer sur le téléphone » (#207, lot 2) : RemoteActivityHelper (AndroidX), qui tire
    // play-services-wearable — permis par la licence (point 4, #213).
    implementation(libs.wear.remote.interactions)
    // La réception de la liaison (Data Layer), permise par la licence (point 4, #213).
    implementation(libs.play.services.wearable)
    // Le client HTTP, les formats du site, l'alerte reçue : ceux du téléphone (#206).
    implementation(project(":partage"))
    // Le chiffrement au Keystore, celui des sessions du téléphone : la montre y garde son jeton (#206).
    implementation(project(":partage-android"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    // Le geste commun des tests d'interface (tests-interface-communs, #216) se compile ici aussi.
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
    debugImplementation(libs.compose.ui.test.manifest)
    // L'aperçu de la tuile : la tuile rendue comme sur la montre, écrite par le test (#207).
    // En DEBUG, pas en androidTest : le moteur lit son thème et ses polices dans les
    // ressources de l'APP rendue, et une dépendance de test ne les y met pas. Jamais en release.
    debugImplementation(libs.wear.tiles.renderer)
    androidTestImplementation(libs.androidx.test.services.storage)
    androidTestUtil(libs.androidx.test.services)
}
