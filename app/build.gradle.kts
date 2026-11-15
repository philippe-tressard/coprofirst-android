// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

import org.gradle.process.ExecOperations
import javax.inject.Inject

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * La VERSION se déduit au build — jamais écrite dans une PR : plusieurs sessions
 * travaillent en parallèle, et deux PR posaient la même (0.9.0). Le socle veut la version
 * posée au tour, pas par chaque lot.
 * - `versionCode` = le nombre de commits de l'historique : chaque fusion sur `main` l'augmente
 *   (Google Play exige un code croissant) ;
 * - `versionName` = `<coprofirst.version.base>.<versionCode>` (« 0.8.13 ») — la base, dans
 *   gradle.properties, ne change que sur une décision de sortie ; l'étiquette posée à la
 *   soumission (#4) reprend ce nom.
 * Un historique incomplet (clone superficiel) ou illisible ARRÊTE le build : une version
 * fausse n'est jamais posée en silence (la CI clone avec `fetch-depth: 0`).
 */
data class VersionDeGit(val code: Int, val nom: String)

val versionDeGit: VersionDeGit = run {
    fun git(vararg args: String): String? = runCatching {
        providers.exec { commandLine("git", *args); isIgnoreExitValue = true }
            .standardOutput.asText.get().trim().takeIf { it.isNotEmpty() }
    }.getOrNull()
    if (git("rev-parse", "--is-shallow-repository") != "false") {
        throw GradleException("❓ Version INCONNUE : historique git incomplet ou illisible (clone superficiel ? fetch-depth: 0)")
    }
    val code = git("rev-list", "--count", "HEAD")?.toIntOrNull()
        ?: throw GradleException("❓ Version INCONNUE : nombre de commits illisible")
    val base = providers.gradleProperty("coprofirst.version.base").orNull
        ?.takeIf { Regex("""\d+\.\d+""").matches(it) }
        ?: throw GradleException("❓ Version INCONNUE : coprofirst.version.base (gradle.properties) absente ou mal formée")
    VersionDeGit(code, "$base.$code")
}

tasks.register("afficherVersion") {
    val v = versionDeGit
    doLast { println("versionCode=${v.code} versionName=${v.nom}") }
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
        versionCode = versionDeGit.code
        versionName = versionDeGit.nom
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
 * tables de navigation, la liste blanche du texte riche et les règles des pièces jointes
 * (→ scripts/generer_tables.py), celles de « Mes lots & accès » (→ scripts/generer_tables_mon_lot.py),
 * celles des « Délégations aidant » (→ scripts/generer_tables_delegations.py),
 * celles du Paramétrage (→ scripts/generer_tables_admin.py)
 * et le logo au format d'Android
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
val genererTablesMonLot = generation("genererTablesMonLot", "contrat/tables-clients.json", "scripts/generer_tables_mon_lot.py")
val genererTablesDelegations =
    generation("genererTablesDelegations", "contrat/tables-clients.json", "scripts/generer_tables_delegations.py")
val genererTablesAdmin = generation("genererTablesAdmin", "contrat/tables-clients.json", "scripts/generer_tables_admin.py")

androidComponents {
    onVariants { variante ->
        variante.sources.kotlin?.addGeneratedSourceDirectory(genererCharte, GenererDepuisPython::sortie)
        variante.sources.kotlin?.addGeneratedSourceDirectory(genererTables, GenererDepuisPython::sortie)
        variante.sources.kotlin?.addGeneratedSourceDirectory(genererTablesMonLot, GenererDepuisPython::sortie)
        variante.sources.kotlin?.addGeneratedSourceDirectory(genererTablesDelegations, GenererDepuisPython::sortie)
        variante.sources.kotlin?.addGeneratedSourceDirectory(genererTablesAdmin, GenererDepuisPython::sortie)
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
