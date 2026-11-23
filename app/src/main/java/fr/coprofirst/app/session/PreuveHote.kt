// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.session

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Call
import okhttp3.Request

/** Ce que sert un hôte pour se dire installation de CoproFirst — comme l'iOS (`PreuveHote`). */
enum class PreuveHote {
    /** Son fichier d'association déclare l'app. */
    Declaree,

    /** Pas de fichier lisible : absent, redirigé, illisible, ou hôte injoignable. */
    Absente,

    /** Un fichier, mais qui ne déclare pas l'app. */
    SansLApp,
    ;

    val reconnue: Boolean get() = this == Declaree

    companion object {
        /**
         * L'app telle que les installations la déclarent, dans le fichier que chacune sert
         * (`/.well-known/apple-app-site-association`) : la preuve de l'iOS, gardée par l'Android
         * pour dire qu'un hôte est une installation — une seule lecture pour les deux apps.
         * `assetlinks.json`, lui, ne sert qu'à Android, qui le lit seul pour vérifier les liens
         * d'application (le manifeste, `autoVerify`).
         */
        const val IDENTIFIANT_APP = "GUX8KASNTG.fr.coprofirst.app"

        /** La taille au-delà de laquelle le fichier n'est pas lu. */
        const val PLAFOND = 128 * 1024

        /**
         * Lit la preuve de l'installation — `Absente` sur toute erreur de transport.
         * Ce fichier n'est PAS une route de l'API (ni dans le contrat, ni sous `/api`) :
         * il se lit ici, et ici seulement (dispense nommée de `verifier-depot.sh`), par
         * le client commun — sans cookie ni redirection.
         */
        suspend fun lire(
            adresse: AdresseInstallation,
            client: Call.Factory = ClientHttp.partage,
            fond: CoroutineDispatcher = Dispatchers.IO,
            racine: String = adresse.url,
        ): PreuveHote = withContext(fond) {
            val requete = Request.Builder().url("$racine/.well-known/apple-app-site-association").build()
            runCatching {
                client.newCall(requete).execute().use { reponse ->
                    val corps = reponse.body.source().apply { request(PLAFOND + 1L) }.buffer
                    if (corps.size > PLAFOND) return@use Absente
                    juger(reponse.code, corps.readUtf8())
                }
            }.getOrDefault(Absente)
        }

        /**
         * La réponse jugée : un 200, un JSON, qui déclare l'app dans `applinks.details`
         * (`appIDs`, ou l'ancien `appID`) ou dans `webcredentials.apps`.
         */
        fun juger(statut: Int, corps: String): PreuveHote {
            if (statut != 200 || corps.length > PLAFOND) return Absente
            val racine = runCatching { Json.parseToJsonElement(corps) as? JsonObject }.getOrNull() ?: return Absente
            val declares = mutableListOf<String>()
            val details = ((racine["applinks"] as? JsonObject)?.get("details") as? JsonArray).orEmpty()
            details.filterIsInstance<JsonObject>().forEach { detail ->
                declares += (detail["appIDs"] as? JsonArray).orEmpty().chaines()
                (detail["appID"] as? JsonPrimitive)?.takeIf { it.isString }?.let { declares += it.content }
            }
            declares += ((racine["webcredentials"] as? JsonObject)?.get("apps") as? JsonArray).orEmpty().chaines()
            return if (IDENTIFIANT_APP in declares) Declaree else SansLApp
        }

        private fun List<*>.chaines(): List<String> =
            filterIsInstance<JsonPrimitive>().filter { it.isString }.map { it.content }
    }
}
