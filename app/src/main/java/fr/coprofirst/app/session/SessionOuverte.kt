// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.session

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call

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
 * Le renouvellement d'une session expirée (POST /auth/refresh) viendra ici, et ici
 * seulement (#13) : aucun service n'a à s'en soucier.
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
        try {
            requete(baseApi(adresse), sessions.client(adresse))
        } catch (erreur: Exception) {
            throw ErreurService.depuis(erreur)
        }
    }
}
