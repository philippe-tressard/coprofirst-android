// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.session

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

/**
 * Une session HTTP par installation, comme l'iOS (`SessionsParHote`) : les jetons
 * voyagent en cookies posés par le serveur, gardés EN MÉMOIRE seulement, jamais
 * partagés d'un hôte à l'autre. Rien n'est écrit sur l'appareil par eux : une session
 * gardée entre deux lancements l'est par `gardees`, après l'accord de l'utilisateur (#13).
 *
 * @param gardees les sessions gardées sur l'appareil : le jeton qui tourne à chaque
 *   renouvellement y est remplacé, sans quoi la prochaine ouverture présenterait un jeton
 *   révoqué.
 */
class SessionsParHote(
    private val base: OkHttpClient = ClientHttp.partage,
    val gardees: SessionsGardees = SessionsGardees.Indisponibles,
    /** Les lectures gardées pour la consultation hors ligne (#28) ; `null` : rien n'est gardé. */
    val lectures: LecturesGardees? = null,
) {
    private val parHote = mutableMapOf<String, Pot>()

    /** LA porte du renouvellement, une pour toutes les résidences (un verrou par hôte). */
    val renouvellement = Renouvellement()

    private val _refusees = MutableSharedFlow<AdresseInstallation>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /**
     * Les sessions que le serveur refuse en cours d'usage, renouvellement compris (#201) :
     * l'entrée quitte la résidence. Comme `SessionsRefusees` de l'iOS.
     */
    val refusees: SharedFlow<AdresseInstallation> = _refusees.asSharedFlow()

    /** Signale le refus — `SessionOuverte` seule : la session est déjà effacée de l'appareil. */
    fun signalerRefus(adresse: AdresseInstallation) {
        _refusees.tryEmit(adresse)
    }

    /** Le client HTTP de cet hôte : le client commun, avec SES cookies. */
    @Synchronized
    fun client(adresse: AdresseInstallation): OkHttpClient = pot(adresse).client

    /** Le cookie de ce nom posé par cet hôte — `null` s'il n'en a pas. */
    @Synchronized
    fun cookie(adresse: AdresseInstallation, nom: String): String? =
        parHote[cle(adresse)]?.cookies?.firstOrNull { it.name == nom }?.value

    /**
     * Pose le jeton de rafraîchissement d'une session gardée, avant de la renouveler —
     * pour l'hôte de `baseApi` (celui de l'adresse, ou le serveur local des tests).
     */
    @Synchronized
    fun poserJeton(adresse: AdresseInstallation, baseApi: String, jeton: String) {
        val url = baseApi.toHttpUrl()
        // Comme le pose le serveur (`COOKIE_OPTS` : Path=/, HttpOnly, Secure) — sécurisé dès
        // que l'adresse l'est, c'est-à-dire toujours hors des tests (le clair est refusé).
        val cookie = Cookie.Builder()
            .name(JETON_RAFRAICHISSEMENT).value(jeton)
            .hostOnlyDomain(url.host).path("/").httpOnly()
            .apply { if (url.isHttps) secure() }
            .expiresAt(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1))
            .build()
        pot(adresse).saveFromResponse(url, listOf(cookie))
    }

    /** Retire la session de cet hôte : la prochaine requête part sans cookie. */
    @Synchronized
    fun oublier(adresse: AdresseInstallation) {
        parHote.remove(cle(adresse))
    }

    /**
     * LA porte de l'effacement de ce qu'une session laisse sur l'appareil — ses cookies, le
     * jeton gardé et les lectures gardées, jamais l'un sans l'autre : la déconnexion, une
     * session que le serveur refuse (à la reprise, en cours d'usage, #201). Comme
     * `oublierCeQuiEstGarde` de l'iOS.
     */
    fun oublierLaSession(adresse: AdresseInstallation) {
        gardees.supprimer(adresse.cleCoffre)
        lectures?.effacer(adresse.cleCoffre)
        oublier(adresse)
    }

    /** Retire la session de chaque hôte. */
    @Synchronized
    fun oublierTout() {
        parHote.clear()
    }

    companion object {
        /** Le cookie du jeton de rafraîchissement, posé par le serveur (`ouvrir_session`). */
        const val JETON_RAFRAICHISSEMENT = "refresh_token"
    }

    private fun pot(adresse: AdresseInstallation): Pot = parHote.getOrPut(cle(adresse)) {
        // LA porte des lectures gardées, dans le client de l'hôte : toute requête y passe.
        Pot(lectures?.let { base.newBuilder().addInterceptor(GarderLesLectures(adresse.cleCoffre, it)).build() } ?: base)
    }

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
