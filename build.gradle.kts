// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

import com.android.build.api.dsl.ApplicationExtension

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.openapi.generator) apply false
}

/**
 * Ce que les APPLICATIONS du dépôt partagent — le téléphone (`:app`) et la montre
 * (`:montre`, #207) sont une seule app Google Play : même identifiant, même release, même
 * clé. Écrit ici une fois, appliqué à tout module `com.android.application`.
 *
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

/**
 * La SIGNATURE de téléversement (Play App Signing, #33) — au poste seulement, JAMAIS en CI.
 * La clé vit hors du dépôt (`docs/signature-android.md`) ; son mot de passe, rangé dans le
 * trousseau macOS, ne passe que par l'environnement le temps d'un build
 * (`scripts/construire-release.sh`, sans cache de configuration). Sans la clé ET le mot de
 * passe, la release se construit non signée : c'est le cas de la CI. Le téléphone et la
 * montre se signent de la même clé : Google Play l'exige pour une même app.
 */
val cleTeleversement: String? = providers.gradleProperty("coprofirst.signature.cle").orNull
val motDePasseTeleversement: String? = providers.environmentVariable("COPROFIRST_SIGNATURE_MDP").orNull
val signatureDuPoste = cleTeleversement != null && motDePasseTeleversement != null

subprojects {
    plugins.withId("com.android.application") {
        extensions.configure<ApplicationExtension> {
            compileSdk {
                version = release(37) { minorApiLevel = 2 }
            }
            defaultConfig {
                // Identifiant aligné sur l'app iOS : il se FIGE à la première publication
                // (liens d'application, compte Play) — en changer ensuite est une nouvelle app.
                applicationId = "fr.coprofirst.app"
                targetSdk = 37
                versionCode = versionDeGit.code
                versionName = versionDeGit.nom
            }
            signingConfigs {
                if (signatureDuPoste) {
                    create("televersement") {
                        storeFile = file(requireNotNull(cleTeleversement))
                        storePassword = motDePasseTeleversement
                        keyAlias = "televersement"
                        keyPassword = motDePasseTeleversement
                    }
                }
            }
            buildTypes {
                getByName("release") {
                    signingConfig = signingConfigs.findByName("televersement")
                    isMinifyEnabled = true
                    isShrinkResources = true
                    proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
                }
            }
            // Les ressources des deux apps, écrites une fois : les règles de sauvegarde et de
            // transfert — rien ne quitte l'appareil (standards/03, standards/14 §1).
            sourceSets.getByName("main").res.srcDir(rootProject.file("ressources-communes"))
            // Le geste et les attentes des tests d'interface, écrits une fois pour le téléphone et la
            // montre (#216) : `toucher` amène l'élément à l'écran avant de toucher.
            sourceSets.getByName("androidTest").kotlin.srcDir(rootProject.file("tests-interface-communs"))
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
        val application = extensions.getByType<ApplicationExtension>().defaultConfig
        tasks.register("afficherVersion") {
            val code = provider { application.versionCode }
            val nom = provider { application.versionName }
            doLast { println("versionCode=${code.get()} versionName=${nom.get()}") }
        }
    }
}
