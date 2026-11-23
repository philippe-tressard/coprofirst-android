// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins


plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * La configuration Firebase des notifications push (#280) : `google-services.json`, gardé
 * HORS de ce dépôt public — au poste, `~/Secrets/CoproFirst/google-services.json`, ou le
 * chemin que donne la propriété Gradle `coprofirst.firebase`. Ses quatre valeurs entrent en
 * ressources, sous les noms que lit `FirebaseOptions.fromResource` (`ConfigFirebase`), et
 * seulement celles de l'app `fr.coprofirst.app`. Absente (la CI, un contributeur) : l'app se
 * construit sans push. Pas de plugin google-services : il exige le fichier dans le dépôt.
 * Lu par `providers.fileContents` : le cache de configuration suit le fichier.
 */
val configFirebase: Map<String, String>? = run {
    val chemin = providers.gradleProperty("coprofirst.firebase")
        .orElse(providers.systemProperty("user.home").map { "$it/Secrets/CoproFirst/google-services.json" })
        .get()
    val texte = providers.fileContents(layout.projectDirectory.file(chemin)).asText.orNull ?: return@run null
    val json = groovy.json.JsonSlurper().parseText(texte) as Map<*, *>
    val projet = json["project_info"] as Map<*, *>
    val client = (json["client"] as List<*>).map { it as Map<*, *> }.firstOrNull { c ->
        ((c["client_info"] as Map<*, *>)["android_client_info"] as Map<*, *>)["package_name"] == "fr.coprofirst.app"
    } ?: error("$chemin : aucune app Android fr.coprofirst.app")
    mapOf(
        "google_app_id" to (client["client_info"] as Map<*, *>)["mobilesdk_app_id"],
        "google_api_key" to ((client["api_key"] as List<*>).first() as Map<*, *>)["current_key"],
        "gcm_defaultSenderId" to projet["project_number"],
        "project_id" to projet["project_id"],
    ).mapValues { (nom, valeur) -> (valeur as? String)?.takeIf { it.isNotBlank() } ?: error("$chemin : $nom absent") }
}

/**
 * Version, signature, SDK, Java, lint : communs au téléphone et à la montre, écrits une
 * fois dans le build racine (`build.gradle.kts`). Ici, le seul propre au téléphone.
 */
android {
    namespace = "fr.coprofirst.app"

    // La configuration Firebase entre en ressources générées (`configFirebase`).
    buildFeatures { resValues = true }
    // Une dispense nommée (`project_id`, lu par Firebase par son nom) : app/lint.xml.
    lint.lintConfig = file("lint.xml")

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        configFirebase?.forEach { (nom, valeur) -> resValue("string", nom, valeur) }
    }
}


val genererTables = generation("genererTables", "contrat/tables-clients.json", "scripts/generer_tables.py")
// Les mesures du site (#2) : les jetons de longueur, que `MesuresTest` confronte à `Mise`.
val genererMesures = generation("genererMesures", "contrat/tables-clients.json", "scripts/generer_mesures.py")

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

// Les composants tiers (#27) : la déclaration commune aux deux apps, confrontée au build à ce
// que la variante publiée embarque, puis copiée dans les ressources que « À propos » lit
// (buildSrc, ComposantsTiers.kt). La montre en fait autant (#317).
val genererComposantsTiers = composantsTiers(autreApp = ":montre")

// Le registre des menus : les inscriptions des écrans (`fonctions/<menu>/Inscription.kt`).
val fonctions = "app/src/main/java/fr/coprofirst/app/fonctions"
val genererRegistre = generation(
    "genererRegistre", fonctions, "scripts/generer_registre.py",
    lus = rootProject.fileTree(fonctions) { include("*/Inscription.kt") },
)

androidComponents {
    onVariants { variante ->
        variante.sources.kotlin?.addGeneratedSourceDirectory(genererTables, GenererDepuisPython::sortie)
        variante.sources.kotlin?.addGeneratedSourceDirectory(genererMesures, GenererDepuisPython::sortie)
        variante.sources.kotlin?.addGeneratedSourceDirectory(genererRegistre, GenererDepuisPython::sortie)
        genererTablesDesMenus.forEach { variante.sources.kotlin?.addGeneratedSourceDirectory(it, GenererDepuisPython::sortie) }
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
    // La posture d'un pliable (#242) : le pli, sa position, à plat ou entrouvert (Jetpack WindowManager).
    implementation(libs.androidx.window)
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
    // La liaison à la montre Wear OS (#207) : le Data Layer, propriétaire, permis par la licence
    // (point 4, décision de Philippe du 16/11/2026, #213). Éteinte tant que LiaisonMontre.ACTIF l'est.
    implementation(libs.play.services.wearable)
    // Les notifications push (#280) : Firebase Cloud Messaging, permis par la licence (point 4,
    // rouvert le 18/11/2026, voie A) ; éteint tant que RelaisPush.ACTIF l'est.
    implementation(libs.firebase.messaging)
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
