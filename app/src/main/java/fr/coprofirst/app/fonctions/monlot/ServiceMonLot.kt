// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.monlot

import fr.coprofirst.api.infrastructure.ClientError
import fr.coprofirst.api.infrastructure.ClientException
import fr.coprofirst.api.infrastructure.ServerError
import fr.coprofirst.api.infrastructure.ServerException
import fr.coprofirst.api.infrastructure.Success
import fr.coprofirst.api.modeles.BailLocataireOut
import fr.coprofirst.api.modeles.CommandeAccesCreate
import fr.coprofirst.api.modeles.DeclarerBadgeBody
import fr.coprofirst.api.modeles.SignalerPerduBody
import fr.coprofirst.api.routes.AccesApi
import fr.coprofirst.api.routes.AuthApi
import fr.coprofirst.api.routes.BailleurApi
import fr.coprofirst.api.routes.LotsApi
import fr.coprofirst.app.commun.acces.TypeAcces
import fr.coprofirst.app.navigation.DroitsOnglet
import fr.coprofirst.app.session.SessionOuverte

/**
 * Ce que « Mes lots & accès » demande à la résidence (#18), comme l'iOS (`ServiceAcces`).
 * Aucune route n'est écrite ici : chacune est une opération du client GÉNÉRÉ. Un geste
 * n'est OFFERT qu'au rôle que la route accepte (`DroitsMonLot`) ; le serveur reste le
 * seul juge, et un refus s'affiche avec sa phrase (`ErreurService.Refus`).
 */
interface ServiceMonLot {
    /** GET /auth/me : ce que le compte EST (son statut), relu à chaque chargement comme l'iOS. */
    suspend fun droits(): DroitsMonLot

    /** GET /lots/mes-lots : les lots du COMPTE, et seulement eux (jamais ceux d'un voisin). */
    suspend fun mesLots(): List<LotDuCompte>

    /** GET /bailleur/mon-bail : le bail du locataire, `null` sans bail. */
    suspend fun monBail(): BailDuLocataire?

    /** GET /bailleur/mes-baux (copropriétaire) ou /bailleur/tous-les-baux (conseil). */
    suspend fun baux(tous: Boolean): List<Bail>

    /** GET /acces/mes-vigiks ou /acces/mes-telecommandes : ce que l'on détient. */
    suspend fun mesAcces(type: TypeAcces): List<AccesDetenu>

    /** GET /bailleur/mes-acces-recus : ce que le bailleur a confié au locataire. */
    suspend fun accesRecus(): List<AccesDetenu>

    /** POST /acces/commandes : demander un accès pour un de SES lots. */
    suspend fun demander(demande: DemandeAcces)

    /** POST /acces/declarer-badge : signaler un accès déjà détenu. Rend `import_resolu`. */
    suspend fun declarer(type: TypeAcces, code: String): Boolean

    /** PATCH /acces/{vigiks|telecommandes}/{id}/perdu. */
    suspend fun signalerPerdu(acces: AccesDetenu)

    /** POST /bailleur/baux/{id}/recuperer-acces : reprendre ce qu'on a confié. */
    suspend fun recupererAcces(bail: Int)
}

/** « Mes lots & accès » par le client généré, à travers LA porte de la résidence. */
class ServiceMonLotApi(private val session: SessionOuverte) : ServiceMonLot {
    override suspend fun droits() = session.appeler { b, c ->
        val moi = AuthApi(b, c).getAuthMe()
        DroitsMonLot(moi.statut.value, DroitsOnglet.depuis(moi))
    }

    override suspend fun mesLots() = session.appeler { b, c -> LotsApi(b, c).getLotsMesLots().map { it.lu() } }

    /**
     * « Pas de bail » est un 200 qui porte `null` (ou un 404) : ce n'est pas une erreur,
     * comme l'iOS. La forme typée du client le refuse (elle force le non-nul) : la réponse
     * se lit donc par sa forme brute, et toute autre issue reprend le chemin du client.
     */
    override suspend fun monBail(): BailDuLocataire? = session.appeler { b, c ->
        when (val reponse = BailleurApi(b, c).getBailleurMonBailWithHttpInfo(accessToken = null)) {
            is Success<*> -> (reponse.data as BailLocataireOut?)?.lu()
            is ClientError<*> -> if (reponse.statusCode == HTTP_INTROUVABLE) {
                null
            } else {
                throw ClientException("Client error : ${reponse.statusCode}", reponse.statusCode, reponse)
            }
            is ServerError<*> -> throw ServerException("Server error : ${reponse.statusCode}", reponse.statusCode, reponse)
            else -> throw UnsupportedOperationException("mon-bail : réponse ${reponse.statusCode}")
        }
    }

    override suspend fun baux(tous: Boolean) = session.appeler { b, c ->
        val api = BailleurApi(b, c)
        (if (tous) api.getBailleurTousLesBaux() else api.getBailleurMesBaux()).map { it.lu() }
    }

    override suspend fun mesAcces(type: TypeAcces) = session.appeler { b, c ->
        val api = AccesApi(b, c)
        when (type) {
            TypeAcces.Vigik -> api.getAccesMesVigiks()
            TypeAcces.Telecommande -> api.getAccesMesTelecommandes()
        }.map { it.lu(type) }
    }

    override suspend fun accesRecus() = session.appeler { b, c -> BailleurApi(b, c).getBailleurMesAccesRecus().map { it.lu() } }

    override suspend fun demander(demande: DemandeAcces) {
        val lot = requireNotNull(demande.lotId) { "un lot est exigé (l'écran le vérifie)" }
        val motif = demande.motif.trim().ifEmpty { null }
        session.appeler { b, c ->
            AccesApi(b, c).postAccesCommandes(CommandeAccesCreate(lot, demande.type.code, demande.quantite, motif))
        }
    }

    override suspend fun declarer(type: TypeAcces, code: String) = session.appeler { b, c ->
        AccesApi(b, c).postAccesDeclarerBadge(DeclarerBadgeBody(type.code, code)).importResolu
    }

    override suspend fun signalerPerdu(acces: AccesDetenu) {
        session.appeler { b, c ->
            val api = AccesApi(b, c)
            if (acces.type == TypeAcces.Telecommande.code) {
                api.patchAccesTelecommandesTcIdPerdu(acces.id, SignalerPerduBody())
            } else {
                api.patchAccesVigiksVigikIdPerdu(acces.id, SignalerPerduBody())
            }
        }
    }

    override suspend fun recupererAcces(bail: Int) {
        session.appeler { b, c -> BailleurApi(b, c).postBailleurBauxBailIdRecupererAcces(bail) }
    }
}

/** Le statut d'une ressource absente : « pas de bail ». */
private const val HTTP_INTROUVABLE = 404
