// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.detail.sondage

import fr.coprofirst.app.commun.masquages.ServiceMasquages
import fr.coprofirst.app.commun.masquages.ServiceMasquagesApi
import fr.coprofirst.app.session.VersionContrat
import fr.coprofirst.api.modeles.CommentaireBody
import fr.coprofirst.api.routes.AuthApi
import fr.coprofirst.api.routes.SondagesApi
import fr.coprofirst.app.commun.ServiceSignalements
import fr.coprofirst.app.commun.ServiceSignalementsApi
import fr.coprofirst.app.session.ErreurService
import fr.coprofirst.app.session.SessionOuverte
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Ce que la fiche d'un sondage demande à la résidence, comme l'iOS (`ServiceSondage`). Le fil
 * et la Communauté l'ouvrent : un composant commun, son service avec lui. Signaler un
 * commentaire : le signalement commun (`ServiceSignalements`) ; masquer son auteur : les
 * masquages communs (`ServiceMasquages`, #297), si l'installation les sert (`moderation`).
 */
interface ServiceSondage : ServiceSignalements, ServiceMasquages {
    /** L'installation sert-elle la modération (`Installation.sertLePlancher`, #311) ? */
    val moderation: Boolean

    /** GET /sondages/{id}, et GET /auth/me pour l'identifiant du compte. */
    suspend fun sondage(id: Int): DetailSondage

    /** POST /sondages/{id}/voter (`VoteBody`) : l'option, la précision, le commentaire. */
    suspend fun voter(vote: VoteSondage, sondage: Int)

    /** POST /sondages/{id}/commenter. */
    suspend fun commenter(texte: String, sondage: Int)

    /** DELETE /sondages/{id}/commentaires/{cid}. */
    suspend fun supprimerCommentaire(commentaire: Int, sondage: Int)
}

/** La fiche d'un sondage par le client GÉNÉRÉ, à travers la porte de la résidence. */
class ServiceSondageApi(private val session: SessionOuverte) :
    ServiceSondage,
    ServiceSignalements by ServiceSignalementsApi(session),
    ServiceMasquages by ServiceMasquagesApi(session) {
    override val moderation: Boolean get() = session.installation.sertLePlancher


    override suspend fun sondage(id: Int): DetailSondage = coroutineScope {
        val compte = async {
            // Le compte illisible n'empêche pas de lire la fiche : chaque commentaire offre
            // alors « Signaler », et le serveur juge (l'iOS de même).
            try {
                session.appeler { base, client -> AuthApi(base, client).getAuthMe() }.id
            } catch (_: ErreurService) {
                null
            }
        }
        val fiche = session.appeler { base, client -> SondagesApi(base, client).getSondagesSondageId(id) }
        DetailSondage.depuis(fiche, compte.await())
    }

    override suspend fun voter(vote: VoteSondage, sondage: Int) {
        val corps = vote.corps() ?: throw ErreurService.Inattendue
        session.appeler { base, client -> SondagesApi(base, client).postSondagesSondageIdVoter(sondage, corps) }
    }

    override suspend fun commenter(texte: String, sondage: Int) {
        val corps = CommentaireBody(contenu = texte)
        session.appeler { base, client -> SondagesApi(base, client).postSondagesSondageIdCommenter(sondage, corps) }
    }

    override suspend fun supprimerCommentaire(commentaire: Int, sondage: Int) {
        session.appeler { base, client ->
            SondagesApi(base, client).deleteSondagesSondageIdCommentairesCommentaireId(sondage, commentaire)
        }
    }
}
