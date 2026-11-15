// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins
//
// Le client de l'API, GÉNÉRÉ à chaque build depuis contrat/openapi.json — la pièce
// jointe de la release de coprofirst au plancher (contrat/plancher.env). Rien de
// généré n'est versionné ; une route absente est un ticket dans coprofirst.

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.openapi.generator)
}

kotlin { jvmToolchain(21) }

val genere = layout.buildDirectory.dir("generated/openapi")

openApiGenerate {
    generatorName.set("kotlin")
    library.set("jvm-okhttp4")
    inputSpec.set(rootProject.file("contrat/openapi.json").path)
    outputDir.set(genere)
    packageName.set("fr.coprofirst.api")
    apiPackage.set("fr.coprofirst.api.routes")
    modelPackage.set("fr.coprofirst.api.modeles")
    generateApiTests.set(false)
    generateModelTests.set(false)
    // kotlinx.serialization ne sait pas sérialiser `Any` : un objet libre du schéma
    // (`additionalProperties: true`) se lit en arbre JSON, sans perte.
    typeMappings.set(
        mapOf(
            "AnyType" to "JsonElement",
            "object" to "JsonObject",
        ),
    )
    importMappings.set(
        mapOf(
            "JsonElement" to "kotlinx.serialization.json.JsonElement",
            "JsonObject" to "kotlinx.serialization.json.JsonObject",
        ),
    )
    configOptions.set(
        mapOf(
            "serializationLibrary" to "kotlinx_serialization",
            "enumPropertyNaming" to "UPPERCASE",
            "sourceFolder" to "src/main/kotlin",
        ),
    )
}

// La version du contrat sur laquelle le client est généré — le PLANCHER — lue dans le
// contrat lui-même (`info.version`), jamais recopiée : l'app refuse une installation
// qui sert un contrat plus ancien (ses réponses ne se décoderaient pas).
val contrat = layout.buildDirectory.dir("generated/contrat")
val genererVersionContrat by tasks.registering {
    val source = rootProject.file("contrat/openapi.json")
    val sortie = contrat
    inputs.file(source)
    outputs.dir(sortie)
    doLast {
        val racine = groovy.json.JsonSlurper().parse(source)
        fun lire(vararg chemin: String): Any? = chemin.fold(racine) { noeud, cle -> (noeud as? Map<*, *>)?.get(cle) }
        val version = lire("info", "version") as String
        require(Regex("""\d+\.\d+\.\d+""").matches(version)) { "info.version illisible : $version" }
        // Ce que le contrat BORNE et que le client généré n'expose pas (comme l'iOS,
        // `LimitesNotifications`) : au-delà, le serveur répond 422.
        val selection = lire("components", "schemas", "SuppressionNotifications", "properties", "ids", "maxItems")
        require(selection is Int && selection > 0) { "SuppressionNotifications.ids.maxItems illisible : $selection" }
        val fichier = sortie.get().file("fr/coprofirst/api/Contrat.kt").asFile
        fichier.parentFile.mkdirs()
        fichier.writeText(
            "// GÉNÉRÉ par client-api/build.gradle.kts depuis contrat/openapi.json — NE PAS MODIFIER.\n" +
                "package fr.coprofirst.api\n\n" +
                "/** La version du contrat d'API sur laquelle ce client est généré : le plancher. */\n" +
                "const val VERSION_CONTRAT_PLANCHER = \"$version\"\n\n" +
                "/** Les identifiants qu'accepte UNE requête POST /notifications/supprimer-selection (`maxItems`). */\n" +
                "const val NOTIFICATIONS_SELECTION_PAR_REQUETE = $selection\n",
        )
    }
}

sourceSets["main"].kotlin.srcDir(genere.map { it.dir("src/main/kotlin") })
sourceSets["main"].kotlin.srcDir(contrat)
tasks.named("compileKotlin") { dependsOn("openApiGenerate", genererVersionContrat) }

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    testImplementation(libs.junit)
}
