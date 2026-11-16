// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.session

import fr.coprofirst.api.infrastructure.ClientException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import kotlin.coroutines.cancellation.CancellationException

/**
 * La résidence où une session est ouverte, et LA porte de ses requêtes : le service de
 * chaque menu appelle le client généré par `appeler`, sur la session HTTP de l'hôte
 * (ses cookies), hors du fil principal, et toute erreur en sort en `ErreurService`.
 *
 * ```
 * interface ServiceProfil { suspend fun moi(): UserRead }
 * class ServiceProfilApi(private val session: SessionOuverte) : ServiceProfil {
 *     override suspend fun moi() = session.appeler { base, client -> AuthApi(base, client).getAuthMe() }
 * }
 * ```
 * Le renouvellement d'une session expirée se fait ici, et ici seulement (#13) : sur un
 * 401, POST /auth/refresh par LA porte (`Renouvellement`), puis la requête se rejoue UNE
 * fois ; un renouvellement refusé, ou un second 401, sort en `SessionExpiree`, comme
 * avant. Aucun service n'a à s'en soucier.
 */
class SessionOuverte(
    val installation: Installation,
    private val sessions: SessionsParHote,
    private val fond: CoroutineDispatcher = Dispatchers.IO,
    private val baseApi: (AdresseInstallation) -> String = { it.baseApi },
) {
    private val adresse: AdresseInstallation = requireNotNull(installation.adresse) { "adresse non normalisée" }

    /** @throws ErreurService toujours ce type : chaque échec a sa phrase. */
    suspend fun <R> appeler(requete: (base: String, client: Call.Factory) -> R): R = withContext(fond) {
        val base = baseApi(adresse)
        try {
            val avant = sessions.renouvellement.releve(adresse.cleCoffre)
            try {
                requete(base, sessions.client(adresse))
            } catch (refus: Exception) {
                // Un 401 du client généré, ou d'une lecture hors contrat (un fichier protégé,
                // qui le dit `SessionExpiree`) : comme `rejouerSurRefus` de l'iOS.
                val expiree = (refus as? ClientException)?.statusCode == 401 || refus == ErreurService.SessionExpiree
                if (!expiree || !renouveler(base, avant)) throw refus
                requete(base, sessions.client(adresse))
            }
        } catch (annulation: CancellationException) {
            throw annulation
        } catch (erreur: Exception) {
            throw ErreurService.depuis(erreur)
        }
    }

    private suspend fun renouveler(base: String, depuis: Int): Boolean =
        sessions.renouvellement.renouveler(adresse.cleCoffre, depuis) { sessions.rafraichir(adresse, base) }
}
