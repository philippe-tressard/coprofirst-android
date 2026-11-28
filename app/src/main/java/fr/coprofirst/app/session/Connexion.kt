// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.session

import fr.coprofirst.api.infrastructure.ClientException
import fr.coprofirst.api.modeles.LoginRequest
import fr.coprofirst.api.routes.AuthApi
import fr.coprofirst.app.navigation.OuvertureResidence
import fr.coprofirst.app.navigation.ServiceCompteApi
import fr.coprofirst.app.navigation.moiOuRien
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

    /**
     * Le compte (GET /auth/me), lu sitôt connecté et AVANT d'ouvrir la résidence (#391, comme
     * `Parcours.ouvrirApresLeCompte` de l'iOS) ; sans compte s'il ne se lit pas — rien ne s'en bloque.
     */
    suspend fun lireLeCompte(installation: Installation) =
        OuvertureResidence(ServiceCompteApi(SessionOuverte(installation, sessions, fond, baseApi)).moiOuRien())

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
        // Refusée : `rafraichir` a déjà tout effacé.
        if (!renouvelee) throw ReprisePerdue
    }

    /**
     * Entrer sans réseau, la biométrie passée (la reprise a échoué faute de réseau) : la
     * dernière lecture gardée s'ouvre en CONSULTATION — plus rien ne part vers cet hôte,
     * hors la reprise de sa session. Une lecture trop ancienne est effacée, et dite.
     */
    fun consulterHorsLigne(installation: Installation): LectureDisponible {
        val adresse = installation.adresse ?: return LectureDisponible.Aucune
        return sessions.lectures?.consulter(adresse.cleCoffre) ?: LectureDisponible.Aucune
    }

    /**
     * Le réseau est revenu : la session se rouvre sans redemander la biométrie (le jeton lu
     * à l'entrée attend dans la session HTTP), et la consultation se referme. Un refus du
     * serveur efface la session gardée et les lectures, et lève `ReprisePerdue`.
     */
    suspend fun reprendreEnLigne(installation: Installation) {
        val adresse = installation.adresse ?: throw ReprisePerdue
        val base = baseApi(adresse)
        val renouvelee = withContext(fond) {
            try {
                sessions.renouvellement.renouveler(adresse.cleCoffre) { sessions.rafraichir(adresse, base) }
            } catch (annulation: CancellationException) {
                throw annulation
            } catch (erreur: Exception) {
                throw ErreurService.depuis(erreur)
            }
        }
        if (!renouvelee) throw ReprisePerdue
        sessions.lectures?.quitterLaConsultation(adresse.cleCoffre)
    }

    /**
     * Se déconnecter : le serveur révoque la session ; quoi qu'il réponde, l'app
     * l'oublie, session gardée comprise — une déconnexion ne se refuse pas. Rend `true` si
     * le serveur a répondu (#177, `ServiceAPI.deconnecter` de l'iOS) : 200, ou 401 — la
     * session y était déjà morte, rien de plus à révoquer ; `false` s'il n'a pas été joint
     * (réseau, 5xx…) : il la révoquera à son expiration.
     */
    suspend fun deconnecter(installation: Installation): Boolean {
        val adresse = installation.adresse ?: return true
        return withContext(fond) {
            val atteint = try {
                AuthApi(baseApi(adresse), sessions.client(adresse)).postAuthLogout()
                true
            } catch (annulee: CancellationException) {
                throw annulee
            } catch (refus: ClientException) {
                refus.statusCode == 401
            } catch (_: Exception) {
                false
            }
            sessions.oublierLaSession(adresse)
            atteint
        }
    }
}

/** L'utilisateur a renoncé à déverrouiller : la session reste gardée, rien ne se dit. */
data object RepriseAnnulee : Exception()

/** La session gardée ne se rouvre plus : le mot de passe, courriel pré-rempli. */
data object ReprisePerdue : Exception()
