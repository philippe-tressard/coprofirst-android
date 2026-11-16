// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins


plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * Version, signature, SDK, Java, lint : communs au téléphone et à la montre, écrits une
 * fois dans le build racine (`build.gradle.kts`). Ici, le seul propre au téléphone.
 */
android {
    namespace = "fr.coprofirst.app"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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


val genererCharte = generation("genererCharte", "contrat/tables-clients.json", "scripts/generer_charte.py")
val genererLogo = generation("genererLogo", "app/src/main/logo/logo.svg", "scripts/generer_logo.py")
val genererTables = generation("genererTables", "contrat/tables-clients.json", "scripts/generer_tables.py")
// Les constantes du serveur (#74) : ce que l'API applique sans le dire dans le contrat.
// Les familles d'affichage des fichiers se lisent à côté, dans tables-clients.json (#2798).
val genererConstantes = generation(
    "genererConstantes", "contrat/constantes-clients.json", "scripts/generer_constantes.py",
    lus = listOf("contrat/constantes-clients.json", "contrat/tables-clients.json"),
)
// Les mesures du site (#2) : les jetons de longueur, que `MesuresTest` confronte à `Mise`.
val genererMesures = generation("genererMesures", "contrat/tables-clients.json", "scripts/generer_mesures.py")
// Les tracés des icônes du site (#183), et leur trait (`attributsTrace`) lu dans tables-clients.json.
val genererTraces = generation(
    "genererTraces", "contrat/icones-svg.json", "scripts/generer_traces.py",
    lus = listOf("contrat/icones-svg.json", "contrat/tables-clients.json"),
)

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
        variante.sources.kotlin?.addGeneratedSourceDirectory(genererMesures, GenererDepuisPython::sortie)
        variante.sources.kotlin?.addGeneratedSourceDirectory(genererTraces, GenererDepuisPython::sortie)
        variante.sources.kotlin?.addGeneratedSourceDirectory(genererRegistre, GenererDepuisPython::sortie)
        genererTablesDesMenus.forEach { variante.sources.kotlin?.addGeneratedSourceDirectory(it, GenererDepuisPython::sortie) }
        variante.sources.res?.addGeneratedSourceDirectory(genererLogo, GenererDepuisPython::sortie)
        variante.sources.assets?.addGeneratedSourceDirectory(genererComposantsTiers, GenererDepuisPython::sortie)
    }
}

dependencies {
    implementation(project(":client-api"))
    // Le client HTTP, les formats du site, l'alerte reçue : partagés avec la montre (#206).
    implementation(project(":partage"))
    implementation(project(":partage-android"))
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
    // Le QR code de la fiche d'arrivée (#6) : la caméra (CameraX) et le décodage (ZXing),
    // libres tous deux et sur l'appareil seul — aucun service de Google Play.
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.zxing.core)
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
