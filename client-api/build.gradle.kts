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

// AU-DESSUS du plancher (#282) : le contrat du plancher, plus les chemins de GREFFES
// (contrat/plancher.env) pris dans contrat/au-dessus/openapi.json, avec les schémas qu'ils
// citent. Comme l'iOS (« Au-dessus du plancher »). Refusé : un chemin greffé déjà au
// plancher (la greffe est morte : retirer la ligne), un schéma cité qui a changé entre les
// deux (la greffe ne réécrit jamais le plancher), un dossier au-dessus/ sans greffe.
val greffe = layout.buildDirectory.file("generated/greffe/openapi.json")
val greffer by tasks.registering {
    val plancher = rootProject.file("contrat/openapi.json")
    val auDessus = rootProject.file("contrat/au-dessus/openapi.json")
    val env = rootProject.file("contrat/plancher.env")
    val sortie = greffe
    inputs.files(plancher, env)
    inputs.files(rootProject.fileTree("contrat/au-dessus"))
    outputs.file(sortie)
    doLast {
        val cles = env.readLines().filter { '=' in it && !it.trimStart().startsWith("#") }
            .associate { it.substringBefore('=').trim() to it.substringAfter('=').trim().removeSurrounding("\"") }
        val chemins = cles["GREFFES"].orEmpty().split(' ').filter { it.isNotBlank() }
        val lecteur = groovy.json.JsonSlurper()
        @Suppress("UNCHECKED_CAST")
        val contrat = lecteur.parse(plancher) as MutableMap<String, Any?>
        if (chemins.isEmpty()) {
            require(!auDessus.exists()) { "contrat/au-dessus/ sans GREFFES : la greffe est morte, retirer le dossier" }
        } else {
            require(auDessus.exists()) { "GREFFES sans contrat/au-dessus/openapi.json : bash scripts/importer-contrat.sh" }
            @Suppress("UNCHECKED_CAST")
            val haut = lecteur.parse(auDessus) as Map<String, Any?>
            @Suppress("UNCHECKED_CAST")
            val routes = contrat["paths"] as MutableMap<String, Any?>
            @Suppress("UNCHECKED_CAST")
            val schemas = (contrat["components"] as MutableMap<String, Any?>)["schemas"] as MutableMap<String, Any?>
            val schemasHaut = (haut["components"] as Map<*, *>)["schemas"] as Map<*, *>
            fun cites(noeud: Any?): List<String> = when (noeud) {
                is Map<*, *> -> noeud.entries.flatMap { (cle, valeur) ->
                    if (cle == "\$ref" && valeur is String) listOf(valeur.substringAfterLast('/')) else cites(valeur)
                }
                is List<*> -> noeud.flatMap { cites(it) }
                else -> emptyList()
            }
            val aGreffer = ArrayDeque<String>()
            chemins.forEach { chemin ->
                require(chemin !in routes) { "greffe morte : $chemin est au plancher — le retirer de GREFFES" }
                val route = (haut["paths"] as Map<*, *>)[chemin] ?: error("$chemin absent de contrat/au-dessus/openapi.json")
                routes[chemin] = route
                aGreffer += cites(route)
            }
            while (aGreffer.isNotEmpty()) {
                val nom = aGreffer.removeFirst()
                val haute = schemasHaut[nom] ?: error("schéma $nom absent de contrat/au-dessus/openapi.json")
                if (nom in schemas) {
                    require(schemas[nom] == haute) { "schéma $nom changé au-dessus du plancher : la greffe ne réécrit pas le plancher" }
                } else {
                    schemas[nom] = haute
                    aGreffer += cites(haute)
                }
            }
        }
        sortie.get().asFile.apply {
            parentFile.mkdirs()
            writeText(groovy.json.JsonOutput.toJson(contrat))
        }
    }
}

openApiGenerate {
    generatorName.set("kotlin")
    // Nos gabarits, là où celui du générateur ne convient pas (lecture des dates, #44).
    templateDir.set(layout.projectDirectory.dir("gabarits").asFile.path)
    library.set("jvm-okhttp4")
    inputSpec.set(greffe)
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
        // La recherche des affaires (#223) : en dessous de `minLength` de `q`, le serveur répond 422.
        val parametres = lire("paths", "/tickets/recherche", "get", "parameters") as? List<*>
        val recherche = parametres?.filterIsInstance<Map<*, *>>()?.firstOrNull { it["name"] == "q" }
            ?.let { (it["schema"] as? Map<*, *>)?.get("minLength") }
        require(recherche is Int && recherche > 0) { "GET /tickets/recherche, q.minLength illisible : $recherche" }
        val fichier = sortie.get().file("fr/coprofirst/api/Contrat.kt").asFile
        fichier.parentFile.mkdirs()
        fichier.writeText(
            "// GÉNÉRÉ par client-api/build.gradle.kts depuis contrat/openapi.json — NE PAS MODIFIER.\n" +
                "package fr.coprofirst.api\n\n" +
                "/** La version du contrat d'API sur laquelle ce client est généré : le plancher. */\n" +
                "const val VERSION_CONTRAT_PLANCHER = \"$version\"\n\n" +
                "/** Les identifiants qu'accepte UNE requête POST /notifications/supprimer-selection (`maxItems`). */\n" +
                "const val NOTIFICATIONS_SELECTION_PAR_REQUETE = $selection\n\n" +
                "/** En dessous, une frappe n'est pas une recherche d'affaires (`minLength` de `q`, `RECHERCHE_MIN` du site). */\n" +
                "const val RECHERCHE_AFFAIRES_MINIMALE = $recherche\n",
        )
    }
}

sourceSets["main"].kotlin.srcDir(genere.map { it.dir("src/main/kotlin") })
sourceSets["main"].kotlin.srcDir(contrat)
tasks.named("openApiGenerate") { dependsOn(greffer) }
tasks.named("compileKotlin") { dependsOn("openApiGenerate", genererVersionContrat) }

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
}
