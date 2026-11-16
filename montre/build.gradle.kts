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
    lint {
        // L'icône vient du logo, généré au build par la couche commune (#206) ; la montre la
        // reçoit avec la complication (#207, lot 5). Rien n'est publié d'ici là.
        disable += "MissingApplicationIcon"
        // L'aperçu de la tuile est une CAPTURE de la tuile rendue, prise sur l'émulateur Wear OS
        // de la CI avec les tests d'interface de la montre (#216, #207) : un dessin fait à la
        // main recopierait des couleurs. Aucune image Wear OS au poste (rien ne s'y installe
        // sans la demande de Philippe).
        disable += "TilePreviews"
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
    implementation(libs.androidx.concurrent.futures)
    // « Continuer sur le téléphone » (#207, lot 2) : RemoteActivityHelper (AndroidX), qui tire
    // play-services-wearable — permis par la licence (point 4, #213).
    implementation(libs.wear.remote.interactions)
    // Le client HTTP, les formats du site, l'alerte reçue : ceux du téléphone (#206).
    implementation(project(":partage"))
    testImplementation(libs.junit)
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
