// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.affaires

import fr.coprofirst.api.modeles.StatutTicket
import fr.coprofirst.api.modeles.TicketEvolutionCreate
import fr.coprofirst.api.modeles.TicketUpdate
import fr.coprofirst.api.routes.LotsApi
import fr.coprofirst.api.routes.PointsConseilApi
import fr.coprofirst.api.routes.TicketsApi
import fr.coprofirst.app.session.ErreurService
import fr.coprofirst.app.session.SessionOuverte
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** Ce que « Mes affaires » demande à la résidence ouverte, comme l'iOS (`ServiceAffaires`). */
interface ServiceAffaires {
    /** GET /tickets : les affaires lisibles par le compte. */
    suspend fun affaires(): List<Affaire>

    /** GET /tickets/referentiels : colonnes du Kanban, états, catégories — servis. */
    suspend fun referentiels(): ReferentielsAffaires

    /** GET /tickets/recherche : les identifiants trouvés, dans l'ordre de pertinence, sans doublon. */
    suspend fun rechercher(terme: String): List<Int>

    /** GET /lots/mes-perimetres : y a-t-il de quoi filtrer « ce qui me concerne » ? Un échec tait la pastille. */
    suspend fun offrirMeConcerne(): Boolean

    /** GET /tickets/{id}/fusion : combien d'affaires pourraient y être fusionnées. */
    suspend fun candidatesALaFusion(id: Int): Int

    /** POST /tickets/{id}/evolutions : une Suite d'état sans texte, qui n'avertit personne — le glissement au Kanban. */
    suspend fun deplacer(id: Int, statut: String)

    /** PATCH /tickets/{id} `archive_manuel` : archiver, jamais supprimer. */
    suspend fun archiver(affaire: Affaire): Affaire

    /** La fiche : GET /tickets/{id}, ses suites, ses messages, ses comptes rendus, sa synthèse. */
    suspend fun affaire(id: Int): DetailAffaire
}

/** Le service par le client généré, sur la session de la résidence — aucune route écrite à la main. */
class ServiceAffairesApi(private val session: SessionOuverte) : ServiceAffaires {
    private suspend fun <R> tickets(requete: TicketsApi.() -> R): R =
        session.appeler { base, client -> TicketsApi(base, client).requete() }

    override suspend fun affaires() = tickets { getTickets().map(Affaire::depuis) }

    override suspend fun referentiels() = tickets { ReferentielsAffaires.depuis(getTicketsReferentiels()) }

    override suspend fun rechercher(terme: String) = tickets { getTicketsRecherche(terme).map { it.ticketId }.distinct() }

    override suspend fun offrirMeConcerne(): Boolean = try {
        val mes = session.appeler { base, client -> LotsApi(base, client).getLotsMesPerimetres() }
        mes.batiments.isNotEmpty() || mes.desservis.isNotEmpty()
    } catch (_: ErreurService) {
        false
    }

    override suspend fun candidatesALaFusion(id: Int) = tickets { getTicketsTicketIdFusion(id).size }

    override suspend fun deplacer(id: Int, statut: String) {
        val nouveau = StatutTicket.decode(statut) ?: throw ErreurService.Inattendue
        tickets { postTicketsTicketIdEvolutions(id, TicketEvolutionCreate(type = "etat", nouveauStatut = nouveau, notifier = false)) }
    }

    override suspend fun archiver(affaire: Affaire): Affaire =
        // La réponse du geste peut se lire sans ses prestataires : on garde ceux qu'on lisait.
        Affaire.depuis(tickets { patchTicketsTicketId(affaire.id, TicketUpdate(archiveManuel = true)) }).copy(prestataires = affaire.prestataires)

    override suspend fun affaire(id: Int): DetailAffaire = coroutineScope {
        val ticket = async { tickets { getTicketsTicketId(id) } }
        val evolutions = async { tickets { getTicketsTicketIdEvolutions(id) } }
        // Des référentiels illisibles n'empêchent pas de lire l'affaire : un état se montre alors par son code.
        val servis = async { ouNul { referentiels() } }
        // Les comptes rendus illisibles se DISENT dans leur rubrique (`null`), sans empêcher la fiche.
        val comptesRendus = async {
            ouNul {
                session.appeler { base, client -> PointsConseilApi(base, client).getPointsConseilAffairesTicketId(id) }
                    .map(CompteRenduAffaire::depuis).sortedByDescending { it.debut }
            }
        }
        var detail = DetailAffaire.lue(ticket.await(), evolutions.await())
            .copy(referentiels = servis.await() ?: ReferentielsAffaires.VIDES, comptesRendus = comptesRendus.await())
        if (detail.close) {
            detail = detail.copy(synthese = ouNul { SyntheseAffaire.depuis(tickets { getTicketsTicketIdSynthese(id) }) })
        }
        // Pas de messages sur une actualité : elle informe, on n'y répond pas.
        if (detail.estActualite) detail else detail.copy(messages = tickets { getTicketsTicketIdMessages(id) }.map(MessageAffaire::depuis))
    }
}

/** Une lecture annexe : son échec vaut `null`, sans empêcher le reste. */
private suspend fun <R> ouNul(lire: suspend () -> R): R? = try {
    lire()
} catch (_: ErreurService) {
    null
}
