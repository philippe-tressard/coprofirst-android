// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.session

import fr.coprofirst.api.infrastructure.ClientException
import fr.coprofirst.api.routes.AuthApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * LA porte du renouvellement de session, comme l'iOS (`Renouvellement`, #81) : la reprise
 * d'une session gardée et le 401 d'une requête en cours d'usage y passent tous deux. Un
 * seul POST /auth/refresh à la fois par hôte : le serveur fait tourner le jeton de
 * rafraîchissement, et un jeton déjà échangé qui revient FERME toutes les sessions du
 * compte (coprofirst#2481) — un second renouvellement concurrent serait ce rejeu.
 */
class Renouvellement {
    private val verrous = mutableMapOf<String, Mutex>()
    /** Les renouvellements réussis, par hôte. */
    private val reussis = mutableMapOf<String, Int>()
    /** Les renouvellements tentés, et l'issue du dernier : qui l'attendait la reçoit. */
    private val tentes = mutableMapOf<String, Int>()
    private val issues = mutableMapOf<String, Boolean>()

    /**
     * Relevé AVANT d'envoyer une requête : revenue refusée alors qu'un renouvellement a
     * réussi depuis, elle partait avec l'ancien jeton — elle se rejoue sans renouveler.
     */
    @Synchronized
    fun releve(hote: String): Int = reussis[hote] ?: 0

    /**
     * Renouvelle la session de cet hôte : `true` si le serveur l'a renouvelée, `false` s'il
     * la refuse ; une erreur de réseau remonte. Un renouvellement en cours est attendu, et
     * son issue rendue, jamais doublé ; un renouvellement réussi depuis `depuis` suffit.
     */
    suspend fun renouveler(hote: String, depuis: Int? = null, rafraichir: suspend () -> Boolean): Boolean {
        val tenteAvant = synchronized(this) { tentes[hote] ?: 0 }
        return verrou(hote).withLock {
            synchronized(this) {
                if (depuis != null && (reussis[hote] ?: 0) > depuis) return@withLock true
                if ((tentes[hote] ?: 0) > tenteAvant) issues[hote]?.let { return@withLock it }
            }
            val renouvele = try {
                rafraichir()
            } catch (erreur: Throwable) {
                // Sans issue (réseau, annulation) : qui attendait tentera lui-même.
                synchronized(this) { issues.remove(hote) }
                throw erreur
            }
            synchronized(this) {
                tentes[hote] = (tentes[hote] ?: 0) + 1
                issues[hote] = renouvele
                if (renouvele) reussis[hote] = (reussis[hote] ?: 0) + 1
            }
            renouvele
        }
    }

    @Synchronized
    private fun verrou(hote: String): Mutex = verrous.getOrPut(hote) { Mutex() }
}

/** La clé d'une installation dans les sessions gardées et le coffre : son hôte (et son port). */
val AdresseInstallation.cleCoffre: String get() = enSaisie

/**
 * POST /auth/refresh par le client généré, sur la session HTTP de l'hôte : `true` si le
 * serveur a renouvelé, `false` s'il refuse (jeton expiré, révoqué) ; le réseau remonte.
 * Le jeton tourne : une session gardée reçoit le neuf, dans le même geste — aucune
 * annulation ne peut séparer la réponse du serveur de la mise à jour de l'appareil.
 * À n'appeler que par `Renouvellement.renouveler`.
 */
internal fun SessionsParHote.rafraichir(adresse: AdresseInstallation, baseApi: String): Boolean {
    try {
        AuthApi(baseApi, client(adresse)).postAuthRefresh()
    } catch (_: ClientException) {
        return false
    }
    val cle = adresse.cleCoffre
    if (gardees.contient(cle)) cookie(adresse, SessionsParHote.JETON_RAFRAICHISSEMENT)?.let { gardees.remplacer(cle, it) }
    return true
}
