// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.communaute

import fr.coprofirst.app.commun.masquages.ServiceMasquages
import fr.coprofirst.app.commun.masquages.ServiceMasquagesApi
import fr.coprofirst.app.session.VersionContrat
import fr.coprofirst.api.modeles.AnnonceCreate
import fr.coprofirst.api.modeles.AnnonceRead
import fr.coprofirst.api.modeles.AnnonceStatutUpdate
import fr.coprofirst.api.modeles.Archivage
import fr.coprofirst.api.modeles.AvisCreate
import fr.coprofirst.api.modeles.AvisListe
import fr.coprofirst.api.modeles.AvisUpdate
import fr.coprofirst.api.modeles.CategorieAnnonce
import fr.coprofirst.api.modeles.IdeeCreate
import fr.coprofirst.api.modeles.IdeeRead
import fr.coprofirst.api.modeles.ReponseCreate
import fr.coprofirst.api.modeles.SondageRead
import fr.coprofirst.api.modeles.StatutAnnonce
import fr.coprofirst.api.modeles.TypeAnnonce
import fr.coprofirst.api.routes.AnnoncesApi
import fr.coprofirst.api.routes.AuthApi
import fr.coprofirst.api.routes.AvisApi
import fr.coprofirst.api.routes.IdesApi
import fr.coprofirst.api.routes.SondagesApi
import fr.coprofirst.app.commun.CibleSignalement
import fr.coprofirst.app.commun.ServiceSignalements
import fr.coprofirst.app.commun.ServiceSignalementsApi
import fr.coprofirst.app.navigation.DroitsOnglet
import fr.coprofirst.app.session.ErreurService
import fr.coprofirst.app.session.SessionOuverte
import java.math.BigDecimal

/**
 * Ce que la Communauté demande à la résidence (#20), comme l'iOS (`ServiceCommunaute`).
 * Ce que le site réserve au conseil syndical ou à l'administration (créer un sondage,
 * changer l'état d'une idée, modérer, répondre aux avis) reste au site, et l'écran le dit.
 * Chaque droit est SERVI : `peut_creer` (sondages), `peut_supprimer` (annonce),
 * `gestionnaire` et `mon_avis_id` (avis), `communaute_motif_refus` (le compte) — l'app ne
 * recalcule aucune règle de rôles. Signaler au conseil syndical : le service commun
 * (`ServiceSignalements`), dont il hérite.
 */
interface ServiceCommunaute : ServiceSignalements, ServiceMasquages {
    /** L'installation sert-elle la modération (`VersionContrat.MODERATION`, #282) : « Masquer ce que publie… ». */
    val moderation: Boolean

    /** GET /auth/me : qui regarde, et la conclusion du serveur sur son accès. */
    suspend fun moi(): MoiCommunaute

    /** GET /sondages. */
    suspend fun sondages(): List<SondageRead>

    /** GET /sondages/gestes → `peut_creer` : ce lecteur crée-t-il un sondage (sur le site) ? */
    suspend fun peutCreerUnSondage(): Boolean

    /** GET /idees ; POST /idees ; POST /idees/{id}/voter (une bascule : le serveur rend sa phrase). */
    suspend fun idees(): List<IdeeRead>
    suspend fun proposerIdee(titre: String, description: String)
    suspend fun voterIdee(id: Int): String?

    /** GET /annonces ; POST /annonces ; PATCH /annonces/{id}/statut ; DELETE /annonces/{id}. */
    suspend fun annonces(): List<AnnonceRead>
    suspend fun deposerAnnonce(saisie: SaisieAnnonce)
    suspend fun changerStatutAnnonce(id: Int, statut: String)
    suspend fun supprimerAnnonce(id: Int)

    /** POST /{idees|annonces}/{id}/reponses ; DELETE …/reponses/{rep}. */
    suspend fun repondre(fil: FilCommunaute, texte: String)
    suspend fun supprimerReponse(fil: FilCommunaute, reponse: Int)

    /** GET /avis ; POST /avis ; PATCH /avis/{id} ; PATCH /avis/{id}/archivage. */
    suspend fun avis(): AvisListe
    suspend fun deposerAvis(saisie: SaisieAvis)
    suspend fun corrigerAvis(id: Int, saisie: SaisieAvis)
    suspend fun archiverAvis(id: Int, archive: Boolean)
}

/** La Communauté par le client GÉNÉRÉ, à travers LA porte de la résidence. */
class ServiceCommunauteApi(private val session: SessionOuverte) :
    ServiceCommunaute,
    ServiceSignalements by ServiceSignalementsApi(session),
    ServiceMasquages by ServiceMasquagesApi(session) {
    override val moderation: Boolean get() = session.installation.sert(VersionContrat.MODERATION)

    override suspend fun moi() = session.appeler { b, c ->
        val moi = AuthApi(b, c).getAuthMe()
        MoiCommunaute(moi.id, DroitsOnglet.depuis(moi), moi.communauteMotifRefus?.takeIf { it.isNotBlank() })
    }

    override suspend fun sondages() = session.appeler { b, c -> SondagesApi(b, c).getSondages() }

    override suspend fun peutCreerUnSondage() = session.appeler { b, c -> SondagesApi(b, c).getSondagesGestes().peutCreer }

    override suspend fun idees() = session.appeler { b, c -> IdesApi(b, c).getIdees() }

    override suspend fun proposerIdee(titre: String, description: String) {
        session.appeler { b, c -> IdesApi(b, c).postIdees(IdeeCreate(titre, description)) }
    }

    override suspend fun voterIdee(id: Int): String? = session.appeler { b, c ->
        IdesApi(b, c).postIdeesIdeeIdVoter(id).message.takeIf { it.isNotBlank() }
    }

    override suspend fun annonces() = session.appeler { b, c -> AnnoncesApi(b, c).getAnnonces() }

    override suspend fun deposerAnnonce(saisie: SaisieAnnonce) {
        val vente = saisie.type == SaisieAnnonce.VENTE
        val corps = AnnonceCreate(
            titre = saisie.titre.trim(),
            description = saisie.descriptionHtml,
            typeAnnonce = TypeAnnonce.decode(saisie.type) ?: throw ErreurService.Inattendue,
            categorie = CategorieAnnonce.decode(saisie.categorie) ?: throw ErreurService.Inattendue,
            prix = if (vente) saisie.prixLu?.let(BigDecimal::valueOf) else null,
            negotiable = vente && saisie.negociable,
            contactVisible = saisie.coordonneesVisibles,
        )
        session.appeler { b, c -> AnnoncesApi(b, c).postAnnonces(corps) }
    }

    override suspend fun changerStatutAnnonce(id: Int, statut: String) {
        val code = StatutAnnonce.decode(statut) ?: throw ErreurService.Inattendue
        session.appeler { b, c -> AnnoncesApi(b, c).patchAnnoncesAnnonceIdStatut(id, AnnonceStatutUpdate(code)) }
    }

    override suspend fun supprimerAnnonce(id: Int) {
        session.appeler { b, c -> AnnoncesApi(b, c).deleteAnnoncesAnnonceId(id) }
    }

    override suspend fun repondre(fil: FilCommunaute, texte: String) {
        val corps = ReponseCreate(texte)
        session.appeler { b, c ->
            when (fil) {
                is FilCommunaute.Idee -> IdesApi(b, c).postIdeesCibleIdReponses(fil.id, corps)
                is FilCommunaute.Annonce -> AnnoncesApi(b, c).postAnnoncesCibleIdReponses(fil.id, corps)
            }
        }
    }

    override suspend fun supprimerReponse(fil: FilCommunaute, reponse: Int) {
        session.appeler { b, c ->
            when (fil) {
                is FilCommunaute.Idee -> IdesApi(b, c).deleteIdeesCibleIdReponsesRepId(fil.id, reponse)
                is FilCommunaute.Annonce -> AnnoncesApi(b, c).deleteAnnoncesCibleIdReponsesRepId(fil.id, reponse)
            }
        }
    }

    override suspend fun avis() = session.appeler { b, c -> AvisApi(b, c).getAvis() }

    override suspend fun deposerAvis(saisie: SaisieAvis) {
        val corps = AvisCreate(saisie.note, saisie.titre.trim(), saisie.descriptionHtml, saisie.remarqueEnvoyee, saisie.consentementEnvoye)
        session.appeler { b, c -> AvisApi(b, c).postAvis(corps) }
    }

    override suspend fun corrigerAvis(id: Int, saisie: SaisieAvis) {
        val corps = AvisUpdate(saisie.note, saisie.titre.trim(), saisie.descriptionHtml, saisie.remarqueEnvoyee, saisie.consentementEnvoye)
        session.appeler { b, c -> AvisApi(b, c).patchAvisAvisId(id, corps) }
    }

    override suspend fun archiverAvis(id: Int, archive: Boolean) {
        session.appeler { b, c -> AvisApi(b, c).patchAvisAvisIdArchivage(id, Archivage(archive)) }
    }
}

/** Le contenu dont on lit et nourrit le fil des réponses — et qu'on signale au conseil. */
sealed interface FilCommunaute {
    val id: Int

    /** Le contenu lui-même, pour POST /signalements (le type que le site envoie). */
    val signalement: CibleSignalement

    data class Idee(override val id: Int) : FilCommunaute {
        override val signalement get() = CibleSignalement("idee", id)
    }

    data class Annonce(override val id: Int) : FilCommunaute {
        override val signalement get() = CibleSignalement("annonce", id)
    }
}
