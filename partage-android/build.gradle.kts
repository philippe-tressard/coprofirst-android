// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

/**
 * Ce que le téléphone (`:app`) et la montre (`:montre`, #207) partagent et qui demande
 * Android (#206, lot 2) : le chiffrement au Keystore ; le rendu du texte riche du site
 * (`TexteDuSite`, `RenduHTML` : une `AnnotatedString`, que chaque app compose dans son
 * propre `Text`), avec sa liste blanche et les constantes du serveur, générées ici. Les
 * palettes, le logo et les libellés du hors ligne au lot suivant. Le Kotlin pur (client
 * HTTP, formats, alerte reçue, découpage du HTML) reste dans `:partage`.
 *
 * Une bibliothèque, pas une application : SDK, Java et lint lui viennent du build racine
 * (`reglagesCommuns`), comme aux deux apps. Les pièces gardent leur paquet d'origine
 * (`fr.coprofirst.app.…`) : les déplacer ne renomme aucun appelant.
 */
plugins {
    alias(libs.plugins.android.library)
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
// La liste blanche du texte riche ; sa forme s'écrit dans generer_tables.py, qu'il charge.
val genererBalises = generation(
    "genererBalises", "contrat/tables-clients.json", "scripts/generer_balises.py",
    lus = listOf("contrat/tables-clients.json", "scripts/generer_tables.py"),
)

androidComponents {
    onVariants { variante ->
        variante.sources.kotlin?.addGeneratedSourceDirectory(genererConstantes, GenererDepuisPython::sortie)
        variante.sources.kotlin?.addGeneratedSourceDirectory(genererBalises, GenererDepuisPython::sortie)
    }
}

dependencies {
    // Le découpage du HTML (`LectureHTML`), Kotlin pur.
    implementation(project(":partage"))
    // `AnnotatedString` : le texte riche rendu, que le téléphone et la montre composent.
    api(platform(libs.compose.bom))
    api(libs.compose.ui.text)
    constraints {
        // Le cycle de vie que tire ui-text, au niveau de celui des deux apps : seul, il resterait
        // en retard (2.10), et ses contrôles de lint obsolètes font échouer le lint strict.
        implementation(libs.androidx.lifecycle.runtime)
    }
    testImplementation(libs.junit)
}
