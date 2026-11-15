// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.session

import androidx.annotation.StringRes
import fr.coprofirst.api.infrastructure.ClientException
import fr.coprofirst.api.infrastructure.ServerException
import fr.coprofirst.app.R
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.net.UnknownHostException

/**
 * Ce qui peut empêcher d'entrer dans une installation — la reconnaître, s'y connecter :
 * chaque cas dit une phrase, celle de l'iOS (\`ErreurService\`), jamais un code brut.
 */
sealed class ErreurEntree(@param:StringRes val phrase: Int) : Exception() {
    /** 421 : l'adresse répond, mais aucune résidence n'y est installée. */
    data object HoteInconnu : ErreurEntree(R.string.erreur_hote_inconnu)

    /** L'adresse ne répond pas (nom inconnu, refus, délai). */
    data object Injoignable : ErreurEntree(R.string.erreur_injoignable)

    /** L'adresse répond, mais ce n'est pas une installation de CoproFirst. */
    data object PasCoproFirst : ErreurEntree(R.string.erreur_pas_coprofirst)

    /** Une installation qui sert un contrat plus ancien que le plancher de l'app. */
    data object ResidenceAMettreAJour : ErreurEntree(R.string.erreur_residence_a_mettre_a_jour)

    /** Adresse électronique ou mot de passe refusés. */
    data object IdentifiantsRefuses : ErreurEntree(R.string.erreur_identifiants_refuses)

    /** Trop d'essais : le serveur demande d'attendre. */
    data object TropDEssais : ErreurEntree(R.string.erreur_trop_d_essais)

    /** Tout le reste : la résidence n'a pas pu répondre. */
    data object Inattendue : ErreurEntree(R.string.erreur_inattendue)

    companion object {
        /**
         * Une erreur à l'ENTRÉE (GET /config d'une adresse donnée) ramenée à un cas :
         * une réponse reçue mais illisible dit que l'adresse ne sert pas CoproFirst.
         */
        fun depuis(erreur: Throwable): ErreurEntree = when (erreur) {
            is ErreurEntree -> erreur
            is ClientException -> when (erreur.statusCode) {
                421 -> HoteInconnu
                else -> PasCoproFirst
            }
            is ServerException -> Inattendue
            is UnknownHostException -> Injoignable
            is SerializationException, is IllegalArgumentException -> PasCoproFirst
            // Une redirection (le client ne la suit pas) : l'adresse ne sert pas l'API ici.
            is UnsupportedOperationException -> PasCoproFirst
            is IOException -> Injoignable
            else -> Inattendue
        }
    }
}
