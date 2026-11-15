// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

import org.gradle.process.ExecOperations
import javax.inject.Inject

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "fr.coprofirst.app"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        // Identifiant aligné sur l'app iOS : il se FIGE à la première publication
        // (liens d'application, compte Play) — en changer ensuite est une nouvelle app.
        applicationId = "fr.coprofirst.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 8
        versionName = "0.8.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures { compose = true }
    lint {
        warningsAsErrors = true
        abortOnError = true
        // Une version plus récente publiée n'est pas un défaut du dépôt : Dependabot la propose.
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
    }
}

/**
 * Ce qui se GÉNÈRE au build par un script Python du dépôt, depuis un fichier recopié à
 * l'octet : les palettes (contrat/tables-clients.json → scripts/generer_charte.py), les
 * tables de navigation (→ scripts/generer_tables.py), celles du Profil
 * (→ scripts/generer_tables_profil.py) et le logo au format d'Android
 * (app/src/main/logo/logo.svg → scripts/generer_logo.py).
 * Rien de généré n'est versionné ; aucune couleur ne s'écrit à la main.
 */
abstract class GenererDepuisPython : DefaultTask() {
    @get:InputFile abstract val entree: RegularFileProperty
    @get:InputFile abstract val generateur: RegularFileProperty
    @get:OutputDirectory abstract val sortie: DirectoryProperty
    @get:Inject abstract val processus: ExecOperations

    @TaskAction
    fun generer() {
        sortie.get().asFile.deleteRecursively()
        processus.exec {
            commandLine("python3", "-I", generateur.get().asFile.path, entree.get().asFile.path, sortie.get().asFile.path)
        }
    }
}

fun generation(nom: String, entree: String, generateur: String) = tasks.register<GenererDepuisPython>(nom) {
    this.entree.set(rootProject.layout.projectDirectory.file(entree))
    this.generateur.set(rootProject.layout.projectDirectory.file(generateur))
    sortie.set(layout.buildDirectory.dir("generated/$nom"))
}

val genererCharte = generation("genererCharte", "contrat/tables-clients.json", "scripts/generer_charte.py")
val genererLogo = generation("genererLogo", "app/src/main/logo/logo.svg", "scripts/generer_logo.py")
val genererTables = generation("genererTables", "contrat/tables-clients.json", "scripts/generer_tables.py")
val genererTablesProfil = generation("genererTablesProfil", "contrat/tables-clients.json", "scripts/generer_tables_profil.py")

androidComponents {
    onVariants { variante ->
        variante.sources.kotlin?.addGeneratedSourceDirectory(genererCharte, GenererDepuisPython::sortie)
        variante.sources.kotlin?.addGeneratedSourceDirectory(genererTables, GenererDepuisPython::sortie)
        variante.sources.kotlin?.addGeneratedSourceDirectory(genererTablesProfil, GenererDepuisPython::sortie)
        variante.sources.res?.addGeneratedSourceDirectory(genererLogo, GenererDepuisPython::sortie)
    }
}

dependencies {
    implementation(project(":client-api"))
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    // L'œil du mot de passe (Visibility) : seules les icônes utilisées restent après R8.
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    // Tests d'interface (émulateur) : le parcours réel, contre un serveur local en HTTPS.
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
    // Celui que tire Compose appelle InputManager.getInstance, retiré d'Android 15 et plus.
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(libs.okhttp.tls)
    debugImplementation(libs.compose.ui.test.manifest)
}
