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
 * constantes du serveur (contrat/constantes-clients.json → scripts/generer_constantes.py), les
 * tables des menus — navigation, texte riche, pièces jointes, natures du fil, et celles
 * de chaque menu (→ scripts/generer_tables*.py, découverts par convention), le registre
 * des menus (fonctions/<menu>/Inscription.kt → scripts/generer_registre.py) et le logo au format d'Android
 * (app/src/main/logo/logo.svg → scripts/generer_logo.py).
 * Rien de généré n'est versionné ; aucune couleur ne s'écrit à la main.
 */
abstract class GenererDepuisPython : DefaultTask() {
    /** Ce que le script lit : un fichier, ou les fichiers d'un dossier (les inscriptions). */
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE) abstract val lus: ConfigurableFileCollection
    /** Le chemin passé au script (le fichier, ou le dossier). */
    @get:Input abstract val entree: Property<String>
    @get:InputFile abstract val generateur: RegularFileProperty
    @get:OutputDirectory abstract val sortie: DirectoryProperty
    /** Ce que le script reçoit après l'entrée et la sortie (la liste des modules embarqués). */
    @get:Input abstract val arguments: ListProperty<String>
    @get:Inject abstract val processus: ExecOperations

    @TaskAction
    fun generer() {
        sortie.get().asFile.deleteRecursively()
        processus.exec {
            commandLine(listOf("python3", "-I", generateur.get().asFile.path, entree.get(), sortie.get().asFile.path) + arguments.get())
        }
    }
}

/**
 * Les modules tiers que l'app EMBARQUE — ceux de la variante publiée (`release`), sans
 * les modules du dépôt —, un `groupe:module` par ligne, pour « À propos » (#27).
 */
abstract class ListerModulesEmbarques : DefaultTask() {
    @get:Input abstract val racine: Property<ResolvedComponentResult>
    @get:OutputFile abstract val sortie: RegularFileProperty

    @TaskAction
    fun lister() {
        val vus = mutableSetOf<ResolvedComponentResult>()
        val aVoir = ArrayDeque(listOf(racine.get()))
        while (aVoir.isNotEmpty()) {
            aVoir.removeFirst().dependencies.filterIsInstance<ResolvedDependencyResult>()
                .map { it.selected }.filter(vus::add).forEach(aVoir::addLast)
        }
        val modules = vus.mapNotNull { (it.id as? ModuleComponentIdentifier)?.let { m -> "${m.group}:${m.module}" } }
        sortie.get().asFile.writeText(modules.distinct().sorted().joinToString("\n", postfix = "\n"))
    }
}

fun generation(nom: String, entree: String, generateur: String, lus: Any = entree) = tasks.register<GenererDepuisPython>(nom) {
    this.lus.from(rootProject.layout.projectDirectory.files(lus))
    this.entree.set(rootProject.layout.projectDirectory.file(entree).asFile.path)
    this.generateur.set(rootProject.layout.projectDirectory.file(generateur))
    sortie.set(layout.buildDirectory.dir("generated/$nom"))
    arguments.convention(emptyList())
}

val genererCharte = generation("genererCharte", "contrat/tables-clients.json", "scripts/generer_charte.py")
val genererLogo = generation("genererLogo", "app/src/main/logo/logo.svg", "scripts/generer_logo.py")
val genererTables = generation("genererTables", "contrat/tables-clients.json", "scripts/generer_tables.py")
// Les constantes du serveur (#74) : ce que l'API applique sans le dire dans le contrat.
val genererConstantes = generation("genererConstantes", "contrat/constantes-clients.json", "scripts/generer_constantes.py")

// Les natures du fil nomment leurs teintes par les jetons de `Couleurs` : generer_tables.py
// les lit dans generer_charte.py (`propriete`, jetons portés), qu'il charge par son chemin.
genererTables.configure { inputs.file(rootProject.layout.projectDirectory.file("scripts/generer_charte.py")) }

// Les tables d'un MENU se découvrent par convention (#109) : chaque
// `scripts/generer_tables_<menu>.py` a sa tâche, `genererTables<Menu>`. Un écran qui ajoute
// ses tables n'ajoute qu'un script, et ne touche jamais ce fichier.
val genererTablesDesMenus = rootProject.layout.projectDirectory.dir("scripts").asFile
    .listFiles { f -> Regex("""generer_tables_[a-z0-9_]+\.py""").matches(f.name) }.orEmpty()
    .sortedBy { it.name }
    .map { script ->
        val nom = script.nameWithoutExtension.split('_').joinToString("") { it.replaceFirstChar(Char::uppercase) }
        generation(nom.replaceFirstChar(Char::lowercase), "contrat/tables-clients.json", "scripts/${script.name}")
    }

// Les composants tiers (#27) : la déclaration (app/src/main/tiers/composants.json),
// confrontée aux modules que la variante publiée embarque — le build s'arrête sur un
// module non déclaré (scripts/generer_composants_tiers.py) —, puis copiée avec ses textes
// dans les ressources de l'app, que « À propos » lit.
val listerModulesEmbarques = tasks.register<ListerModulesEmbarques>("listerModulesEmbarques") {
    racine.set(provider { configurations.getByName("releaseRuntimeClasspath") }.flatMap { it.incoming.resolutionResult.rootComponent })
    sortie.set(layout.buildDirectory.file("modules-embarques.txt"))
}
val genererComposantsTiers = generation(
    "genererComposantsTiers", "app/src/main/tiers/composants.json", "scripts/generer_composants_tiers.py",
    lus = listOf("app/src/main/tiers", "LICENSES"),
)
genererComposantsTiers.configure {
    val liste = listerModulesEmbarques.flatMap { it.sortie }
    lus.from(liste)
    arguments.add(liste.map { it.asFile.path })
}

// Le registre des menus : les inscriptions des écrans (`fonctions/<menu>/Inscription.kt`).
val fonctions = "app/src/main/java/fr/coprofirst/app/fonctions"
val genererRegistre = generation(
    "genererRegistre", fonctions, "scripts/generer_registre.py",
    lus = rootProject.fileTree(fonctions) { include("*/Inscription.kt") },
)

androidComponents {
    onVariants { variante ->
        variante.sources.kotlin?.addGeneratedSourceDirectory(genererCharte, GenererDepuisPython::sortie)
        variante.sources.kotlin?.addGeneratedSourceDirectory(genererTables, GenererDepuisPython::sortie)
        variante.sources.kotlin?.addGeneratedSourceDirectory(genererConstantes, GenererDepuisPython::sortie)
        variante.sources.kotlin?.addGeneratedSourceDirectory(genererRegistre, GenererDepuisPython::sortie)
        genererTablesDesMenus.forEach { variante.sources.kotlin?.addGeneratedSourceDirectory(it, GenererDepuisPython::sortie) }
        variante.sources.res?.addGeneratedSourceDirectory(genererLogo, GenererDepuisPython::sortie)
        variante.sources.assets?.addGeneratedSourceDirectory(genererComposantsTiers, GenererDepuisPython::sortie)
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
