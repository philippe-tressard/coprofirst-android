// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.session

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient

/**
 * Une session HTTP par installation, comme l'iOS (`SessionsParHote`) : les jetons
 * voyagent en cookies posés par le serveur, gardés EN MÉMOIRE seulement, jamais
 * partagés d'un hôte à l'autre. Rien n'est écrit sur l'appareil : une session gardée
 * entre deux lancements viendra avec la biométrie (coffre du compte).
 */
class SessionsParHote(private val base: OkHttpClient = ClientHttp.partage) {
    private val parHote = mutableMapOf<String, Pot>()

    /** Le client HTTP de cet hôte : le client commun, avec SES cookies. */
    @Synchronized
    fun client(adresse: AdresseInstallation): OkHttpClient = pot(adresse).client

    /** Le cookie de ce nom posé par cet hôte — `null` s'il n'en a pas. */
    @Synchronized
    fun cookie(adresse: AdresseInstallation, nom: String): String? =
        parHote[cle(adresse)]?.cookies?.firstOrNull { it.name == nom }?.value

    /** Retire la session de cet hôte : la prochaine requête part sans cookie. */
    @Synchronized
    fun oublier(adresse: AdresseInstallation) {
        parHote.remove(cle(adresse))
    }

    private fun pot(adresse: AdresseInstallation): Pot = parHote.getOrPut(cle(adresse)) { Pot(base) }

    private fun cle(adresse: AdresseInstallation) = adresse.url

    /** Les cookies d'UN hôte, en mémoire ; OkHttp n'en rend qu'aux requêtes qui leur correspondent. */
    private class Pot(base: OkHttpClient) : CookieJar {
        var cookies: List<Cookie> = emptyList()
            private set
        val client: OkHttpClient = base.newBuilder().cookieJar(this).build()

        @Synchronized
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val noms = cookies.map { it.name to it.path }.toSet()
            val maintenant = System.currentTimeMillis()
            this.cookies = (this.cookies.filterNot { (it.name to it.path) in noms } + cookies)
                .filter { it.expiresAt > maintenant }
        }

        @Synchronized
        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val maintenant = System.currentTimeMillis()
            return cookies.filter { it.expiresAt > maintenant && it.matches(url) }
        }
    }
}
