// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.session

import fr.coprofirst.api.infrastructure.ClientException
import fr.coprofirst.api.modeles.LoginRequest
import fr.coprofirst.api.routes.AuthApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * La connexion à une installation : POST /auth/login par le client généré, sur la
 * session HTTP de l'hôte (les jetons y restent, en cookies, en mémoire). Seule la garde
 * de l'hôte l'appelle : aucun écran n'envoie un mot de passe sans y être passé.
 */
class Connexion(
    private val sessions: SessionsParHote = SessionsParHote(),
    private val fond: CoroutineDispatcher = Dispatchers.IO,
    private val baseApi: (AdresseInstallation) -> String = { it.baseApi },
) {
    /** @throws ErreurEntree toujours ce type : chaque échec a sa phrase. */
    suspend fun connecter(installation: Installation, email: String, motDePasse: String) {
        val adresse = installation.adresse ?: throw ErreurEntree.Inattendue
        withContext(fond) {
            try {
                AuthApi(baseApi(adresse), sessions.client(adresse)).postAuthLogin(LoginRequest(email.trim(), motDePasse))
            } catch (erreur: ClientException) {
                throw when (erreur.statusCode) {
                    401, 403, 422 -> ErreurEntree.IdentifiantsRefuses
                    429 -> ErreurEntree.TropDEssais
                    else -> ErreurEntree.depuis(erreur)
                }
            } catch (erreur: Exception) {
                throw ErreurEntree.depuis(erreur)
            }
        }
    }

    /**
     * Se déconnecter : le serveur révoque la session ; quoi qu'il réponde, l'app
     * l'oublie — une déconnexion ne se refuse pas.
     */
    suspend fun deconnecter(installation: Installation) {
        val adresse = installation.adresse ?: return
        withContext(fond) {
            runCatching { AuthApi(baseApi(adresse), sessions.client(adresse)).postAuthLogout() }
            sessions.oublier(adresse)
        }
    }
}
