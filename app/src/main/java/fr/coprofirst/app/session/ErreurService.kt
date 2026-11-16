// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.session

import androidx.annotation.StringRes
import fr.coprofirst.api.infrastructure.ClientError
import fr.coprofirst.api.infrastructure.ClientException
import fr.coprofirst.api.infrastructure.ServerException
import fr.coprofirst.app.R
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.IOException
import java.net.UnknownHostException

/**
 * Ce qui peut empêcher d'entrer ou d'agir, comme l'iOS (`ErreurService`) : chaque cas dit
 * une phrase, jamais un code brut — celle du SERVEUR quand il en donne une (`Refus`).
 * Un écran l'affiche par `MessageErreur` ; il ne compose jamais sa propre phrase d'échec
 * pour une erreur que ce type sait dire.
 */
sealed class ErreurService(@param:StringRes val phraseRes: Int) : Exception() {
    /** 421 : l'adresse répond, mais aucune résidence n'y est installée. */
    data object HoteInconnu : ErreurService(R.string.erreur_hote_inconnu)

    /** La résidence est consultée hors ligne : ce geste attend le réseau (#28). */
    data object HorsLigne : ErreurService(R.string.erreur_hors_ligne)

    /** L'adresse ne répond pas (nom inconnu, refus, délai). */
    data object Injoignable : ErreurService(R.string.erreur_injoignable)

    /** L'adresse répond, mais ce n'est pas une installation de CoproFirst. */
    data object PasCoproFirst : ErreurService(R.string.erreur_pas_coprofirst)

    /** Une installation qui sert un contrat plus ancien que le plancher de l'app. */
    data object ResidenceAMettreAJour : ErreurService(R.string.erreur_residence_a_mettre_a_jour)

    /** Adresse électronique ou mot de passe refusés. */
    data object IdentifiantsRefuses : ErreurService(R.string.erreur_identifiants_refuses)

    /** Trop d'essais : le serveur demande d'attendre. */
    data object TropDEssais : ErreurService(R.string.erreur_trop_d_essais)

    /** La session a expiré : il faut se reconnecter. */
    data object SessionExpiree : ErreurService(R.string.erreur_session_expiree)

    /** L'élément n'existe plus, ou n'est pas lisible par ce compte. */
    data object Introuvable : ErreurService(R.string.erreur_introuvable)

    /** Un geste refusé, avec la phrase du serveur (`detail`) — la sienne, pas la nôtre. */
    data class Refus(val phraseServie: String, val statut: Int) : ErreurService(R.string.erreur_inattendue)

    /** Tout le reste : la résidence n'a pas pu répondre (le code reste dans le journal). */
    data object Inattendue : ErreurService(R.string.erreur_inattendue)

    companion object {
        /**
         * Une erreur d'une requête dans une résidence ouverte, ramenée à un cas. Une
         * réponse REÇUE mais illisible n'est pas « pas une résidence CoproFirst » : le
         * serveur a répondu, c'est `Inattendue` (ce jugement-là est réservé à l'entrée).
         */
        fun depuis(erreur: Throwable): ErreurService = when (erreur) {
            is ErreurService -> erreur
            is ClientException -> refus(erreur) ?: depuisStatut(erreur.statusCode)
            is ServerException -> Inattendue
            is HorsLigneException -> HorsLigne
            is IOException -> Injoignable
            else -> Inattendue
        }

        /**
         * Le statut d'une LECTURE sans phrase servie (un fichier protégé, comme l'iOS
         * `depuisLecture`) : 401, une session expirée ; 403 et 404, un élément qu'on ne
         * peut pas ouvrir ; 429, trop d'essais ; tout autre, inattendu.
         */
        fun depuisStatut(statut: Int): ErreurService = when (statut) {
            401 -> SessionExpiree
            403, 404 -> Introuvable
            429 -> TropDEssais
            else -> Inattendue
        }

        /**
         * À l'ENTRÉE dans une installation (GET /config d'une adresse donnée) : une
         * réponse illisible, une redirection ou un 4xx disent que l'adresse ne sert pas
         * CoproFirst ; 421, qu'aucune résidence n'y est installée.
         */
        fun depuisEntree(erreur: Throwable): ErreurService = when (erreur) {
            is ErreurService -> erreur
            is ClientException -> if (erreur.statusCode == 421) HoteInconnu else PasCoproFirst
            is ServerException -> Inattendue
            is UnknownHostException -> Injoignable
            is SerializationException, is IllegalArgumentException -> PasCoproFirst
            // Une redirection (le client ne la suit pas) : l'adresse ne sert pas l'API ici.
            is UnsupportedOperationException -> PasCoproFirst
            is IOException -> Injoignable
            else -> Inattendue
        }

        /** La phrase du serveur (`{"detail": "…"}`), s'il en a donné une en texte. */
        fun refus(erreur: ClientException): Refus? {
            val corps = (erreur.response as? ClientError<*>)?.body as? String ?: return null
            val detail = runCatching { (Json.parseToJsonElement(corps) as? JsonObject)?.get("detail") }.getOrNull()
            val phrase = (detail as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()
            return phrase?.takeIf { it.isNotEmpty() }?.let { Refus(it, erreur.statusCode) }
        }
    }
}
