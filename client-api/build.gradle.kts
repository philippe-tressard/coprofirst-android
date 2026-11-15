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

sourceSets["main"].kotlin.srcDir(genere.map { it.dir("src/main/kotlin") })
tasks.named("compileKotlin") { dependsOn("openApiGenerate") }

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    testImplementation(libs.junit)
}
