// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

/**
 * Ce que le téléphone (`:app`) et la montre (`:montre`, #207) partagent et qui demande
 * Android (#206, lot 2) : le chiffrement au Keystore ; le rendu du texte riche du site
 * (`TexteDuSite`, `RenduHTML` : une `AnnotatedString`, que chaque app compose dans son
 * propre `Text`), avec sa liste blanche et les constantes du serveur ; les palettes de la
 * charte, le logo et les tracés des icônes du site (`TracesDuSite`), générés ici ; les types
 * d'alerte (`TypesNotification`) ; les lectures gardées hors ligne (`LecturesGardees`, sa
 * porte `GarderLesLectures`, la liste de chaque app `ListeDesLectures`) et leurs libellés. Ses
 * ressources sont dans SON `R` (`fr.coprofirst.app.partage.R`, nonTransitiveRClass). Le
 * Kotlin pur (client HTTP, formats, alerte reçue, découpage du HTML) reste dans `:partage`.
 *
 * Une bibliothèque, pas une application : SDK, Java et lint lui viennent du build racine
 * (`reglagesCommuns`), comme aux deux apps. Les pièces gardent leur paquet d'origine
 * (`fr.coprofirst.app.…`) : les déplacer ne renomme aucun appelant.
 */
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "fr.coprofirst.app.partage"
}

// Les constantes du serveur (#74) : ce que l'API applique sans le dire dans le contrat — dont
// les schémas d'un lien touchable du texte riche. Les familles d'affichage des fichiers se
// lisent à côté, dans tables-clients.json (#2798).
val genererConstantes = generation(
    "genererConstantes", "contrat/constantes-clients.json", "scripts/generer_constantes.py",
    lus = listOf("contrat/constantes-clients.json", "contrat/tables-clients.json"),
)
// Les palettes de la charte (`Couleurs`, `Palettes`), clair et sombre : la marque de danger et
// la couleur du point de la montre sont celles du téléphone.
val genererCharte = generation("genererCharte", "contrat/tables-clients.json", "scripts/generer_charte.py")
// Le logo au format d'Android (icône adaptative, médaillon de l'accueil) depuis le dessin du
// site recopié à l'identique (scripts/importer-logo.sh), sous sa licence propre.
val genererLogo = generation("genererLogo", "partage-android/src/main/logo/logo.svg", "scripts/generer_logo.py")
// Les tracés des icônes du site (#183), et leur trait (`attributsTrace`) lu dans
// tables-clients.json : la gouttière du fil du téléphone, l'icône du type d'une alerte.
val genererTraces = generation(
    "genererTraces", "contrat/icones-svg.json", "scripts/generer_traces.py",
    lus = listOf("contrat/icones-svg.json", "contrat/tables-clients.json"),
)
// La liste blanche du texte riche ; sa forme s'écrit dans generer_tables.py, qu'il charge.
val genererBalises = generation(
    "genererBalises", "contrat/tables-clients.json", "scripts/generer_balises.py",
    lus = listOf("contrat/tables-clients.json", "scripts/generer_tables.py"),
)

androidComponents {
    onVariants { variante ->
        variante.sources.kotlin?.addGeneratedSourceDirectory(genererConstantes, GenererDepuisPython::sortie)
        variante.sources.kotlin?.addGeneratedSourceDirectory(genererBalises, GenererDepuisPython::sortie)
        variante.sources.kotlin?.addGeneratedSourceDirectory(genererCharte, GenererDepuisPython::sortie)
        variante.sources.kotlin?.addGeneratedSourceDirectory(genererTraces, GenererDepuisPython::sortie)
        variante.sources.res?.addGeneratedSourceDirectory(genererLogo, GenererDepuisPython::sortie)
    }
}

dependencies {
    // Le découpage du HTML (`LectureHTML`), Kotlin pur ; le client HTTP et OkHttp, que la porte
    // des lectures gardées (`GarderLesLectures`, `ListeDesLectures`) expose.
    api(project(":partage"))
    // L'index chiffré des lectures gardées.
    implementation(libs.kotlinx.serialization.json)
    // `AnnotatedString` : le texte riche rendu, que le téléphone et la montre composent.
    api(platform(libs.compose.bom))
    api(libs.compose.ui.text)
    // `ImageVector` : les tracés des icônes du site (`TracesDuSite`).
    api(libs.compose.ui)
    constraints {
        // Le cycle de vie que tire ui-text, au niveau de celui des deux apps : seul, il resterait
        // en retard (2.10), et ses contrôles de lint obsolètes font échouer le lint strict.
        implementation(libs.androidx.lifecycle.runtime)
    }
    testImplementation(libs.junit)
}
