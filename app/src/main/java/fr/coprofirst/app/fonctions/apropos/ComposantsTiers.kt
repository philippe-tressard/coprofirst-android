// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.apropos

import android.content.res.AssetManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Un composant tiers embarqué dans l'app, comme l'iOS (`ComposantTiers`) : son nom, ses
 * licences, son rôle, les modules qu'il couvre, et les textes qui doivent accompagner
 * chaque copie — l'avis de ses auteurs, puis la licence entière.
 */
@Serializable
data class ComposantTiers(
    val nom: String,
    val licences: List<String>,
    val role: String,
    val modules: List<String>,
    val textes: List<String>,
)

@Serializable
private data class Declaration(val composants: List<ComposantTiers>)

/**
 * Ce que l'app embarque d'autrui : la déclaration `app/src/main/tiers/composants.json`,
 * confrontée au build aux modules de la variante publiée (scripts/generer_composants_tiers.py
 * refuse un module non déclaré, une licence sans son texte), copiée dans les ressources avec
 * ses textes — rien ne s'écrit ici.
 */
object ComposantsTiers {
    private const val DOSSIER = "tiers"
    private val json = Json { ignoreUnknownKeys = true }

    fun lire(texte: String): List<ComposantTiers> = json.decodeFromString<Declaration>(texte).composants

    fun tous(ressources: AssetManager): List<ComposantTiers> =
        lire(ressources.open("$DOSSIER/composants.json").bufferedReader().use { it.readText() })

    /** Les textes du composant, dans l'ordre. Un texte absent n'a pas de remplaçant : le build l'a refusé. */
    fun textes(ressources: AssetManager, composant: ComposantTiers): List<String> =
        composant.textes.map { nom -> ressources.open("$DOSSIER/$nom").bufferedReader().use { it.readText() } }
}
