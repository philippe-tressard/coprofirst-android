// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.session

import fr.coprofirst.api.infrastructure.ClientException
import fr.coprofirst.api.modeles.LoginRequest
import fr.coprofirst.api.routes.AuthApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

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
    /** @throws ErreurService toujours ce type : chaque échec a sa phrase. */
    suspend fun connecter(installation: Installation, email: String, motDePasse: String) {
        val adresse = installation.adresse ?: throw ErreurService.Inattendue
        withContext(fond) {
            try {
                AuthApi(baseApi(adresse), sessions.client(adresse)).postAuthLogin(LoginRequest(email.trim(), motDePasse))
            } catch (erreur: ClientException) {
                throw when (erreur.statusCode) {
                    401, 403, 422 -> ErreurService.IdentifiantsRefuses
                    429 -> ErreurService.TropDEssais
                    else -> ErreurService.depuis(erreur)
                }
            } catch (erreur: Exception) {
                throw ErreurService.depuis(erreur)
            }
        }
    }

    /** Une session est gardée sur l'appareil pour cette résidence (sans rien lire). */
    fun aUneSessionGardee(installation: Installation): Boolean =
        installation.adresse?.let { sessions.gardees.contient(it.cleCoffre) } == true

    /**
     * « Activer » : la session ouverte se garde sur l'appareil (son jeton de
     * rafraîchissement), sous la meilleure protection qu'il offre — rendue pour que l'écran
     * la dise ; `null` si rien n'a pu être gardé. Comme `activerFaceID` de l'iOS.
     */
    suspend fun garder(installation: Installation): ProtectionSession? {
        val adresse = installation.adresse ?: return null
        val jeton = sessions.cookie(adresse, SessionsParHote.JETON_RAFRAICHISSEMENT) ?: return null
        return withContext(fond) {
            sessions.gardees.garder(adresse.cleCoffre, jeton)?.takeIf { sessions.gardees.contient(adresse.cleCoffre) }
        }
    }

    /**
     * Rouvre une session gardée : la biométrie (ou le code) déverrouille le jeton, puis
     * POST /auth/refresh par LA porte du renouvellement — le jeton neuf y remplace l'ancien.
     * @throws ReprisePerdue quand rien ne se rouvre plus (jeton refusé, biométrie changée) :
     *   la session gardée est effacée ;
     * @throws RepriseAnnulee quand l'utilisateur renonce : elle reste gardée ;
     * @throws ErreurService pour le reste (réseau…) : elle reste gardée.
     */
    suspend fun reprendre(installation: Installation, deverrouillage: Deverrouillage) {
        val adresse = installation.adresse ?: throw ReprisePerdue
        val cle = adresse.cleCoffre
        val jeton = when (val lecture = sessions.gardees.lire(cle, deverrouillage)) {
            is LectureSession.Jeton -> lecture.valeur
            LectureSession.Annulee -> throw RepriseAnnulee
            LectureSession.Perdue -> throw ReprisePerdue
        }
        val base = baseApi(adresse)
        val renouvelee = withContext(fond) {
            try {
                sessions.poserJeton(adresse, base, jeton)
                sessions.renouvellement.renouveler(cle) { sessions.rafraichir(adresse, base) }
            } catch (annulation: CancellationException) {
                throw annulation
            } catch (erreur: IOException) {
                throw ErreurService.depuis(erreur)
            } catch (erreur: RuntimeException) {
                throw ErreurService.depuis(erreur)
            }
        }
        if (!renouvelee) {
            sessions.oublier(adresse)
            sessions.gardees.supprimer(cle)
            throw ReprisePerdue
        }
    }

    /**
     * Se déconnecter : le serveur révoque la session ; quoi qu'il réponde, l'app
     * l'oublie, session gardée comprise — une déconnexion ne se refuse pas.
     */
    suspend fun deconnecter(installation: Installation) {
        val adresse = installation.adresse ?: return
        withContext(fond) {
            runCatching { AuthApi(baseApi(adresse), sessions.client(adresse)).postAuthLogout() }
            sessions.gardees.supprimer(adresse.cleCoffre)
            sessions.oublier(adresse)
        }
    }
}

/** L'utilisateur a renoncé à déverrouiller : la session reste gardée, rien ne se dit. */
data object RepriseAnnulee : Exception()

/** La session gardée ne se rouvre plus : le mot de passe, courriel pré-rempli. */
data object ReprisePerdue : Exception()
