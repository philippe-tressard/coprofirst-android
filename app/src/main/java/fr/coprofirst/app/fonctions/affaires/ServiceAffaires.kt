// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.affaires

import fr.coprofirst.api.modeles.CategorieTicket
import fr.coprofirst.api.modeles.MessageCreate
import fr.coprofirst.api.modeles.MessageUpdate
import fr.coprofirst.api.modeles.StatutTicket
import fr.coprofirst.api.modeles.TicketCreate
import fr.coprofirst.api.modeles.TicketEvolutionCreate
import fr.coprofirst.api.modeles.TicketEvolutionUpdate
import fr.coprofirst.api.modeles.TicketUpdate
import fr.coprofirst.api.routes.LotsApi
import fr.coprofirst.api.routes.PointsConseilApi
import fr.coprofirst.api.routes.PrestatairesApi
import fr.coprofirst.api.routes.TicketsApi
import fr.coprofirst.app.commun.apercu.ServiceLectures
import fr.coprofirst.app.commun.ServiceSignalements
import fr.coprofirst.app.commun.ServiceSignalementsApi
import fr.coprofirst.app.commun.apercu.ServiceLecturesApi
import fr.coprofirst.app.commun.masquages.ServiceMasquages
import fr.coprofirst.app.commun.masquages.ServiceMasquagesApi
import fr.coprofirst.app.commun.perimetres.ServicePerimetres
import fr.coprofirst.app.commun.perimetres.ServicePerimetresApi
import fr.coprofirst.app.commun.texte.TexteDuSite
import fr.coprofirst.app.session.ErreurService
import fr.coprofirst.app.session.SessionOuverte
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Ce que « Mes affaires » demande à la résidence ouverte, comme l'iOS (`ServiceAffaires`) —
 * les lieux compris (`ServicePerimetres`, commun).
 */
interface ServiceAffaires : ServicePerimetres, ServiceLectures, ServiceSignalements, ServiceMasquages {
    /** GET /tickets : les affaires lisibles par le compte. */
    suspend fun affaires(): List<Affaire>

    /** GET /tickets/referentiels : colonnes du Kanban, états, catégories — servis. */
    suspend fun referentiels(): ReferentielsAffaires

    /** GET /prestataires : ceux de la résidence, non archivés — le choix de « Modifier » (#182). */
    suspend fun prestatairesDeLaResidence(): List<PrestataireAffaire>

    /** GET /tickets/recherche : les identifiants trouvés, dans l'ordre de pertinence, sans doublon. */
    suspend fun rechercher(terme: String): List<Int>

    /** GET /lots/mes-perimetres : y a-t-il de quoi filtrer « ce qui me concerne » ? Un échec tait la pastille. */
    suspend fun offrirMeConcerne(): Boolean

    /** GET /tickets/{id}/fusion : combien d'affaires pourraient y être fusionnées. */
    suspend fun candidatesALaFusion(id: Int): Int

    /** POST /tickets/{id}/evolutions : une Suite d'état sans texte, qui n'avertit personne — le glissement au Kanban. */
    suspend fun deplacer(id: Int, statut: String)

    /** PATCH /tickets/{id} `archive_manuel` : archiver, jamais supprimer. */
    suspend fun archiver(id: Int): Affaire

    /** La fiche : GET /tickets/{id}, ses suites, ses messages, ses comptes rendus, sa synthèse. */
    suspend fun affaire(id: Int): DetailAffaire

    /** POST /tickets : « Nouvelle affaire ». */
    suspend fun creer(saisie: SaisieAffaire): Affaire

    /** POST /tickets/{id}/messages : « Répondre » — un message du fil, que le serveur double d'une suite. */
    suspend fun repondre(id: Int, texte: String)

    /** PATCH /tickets/{id}/messages/{msg_id} : « Modifier mon message » — le texte seul, les pièces jointes restent. */
    suspend fun corrigerMessage(id: Int, message: Int, texte: String)

    /** DELETE /tickets/{id}/messages/{msg_id} : « Supprimer mon message » ; le serveur retire aussi son miroir de l'Historique. */
    suspend fun supprimerMessage(id: Int, message: Int)

    /** POST /tickets/{id}/evolutions : « Ajouter une suite » — un commentaire, ou un nouvel état. */
    suspend fun ajouterSuite(id: Int, suite: SaisieSuite)

    /** PATCH /tickets/{id} : une option de mise en avant, posée ou retirée. */
    suspend fun regler(id: Int, option: OptionAffaire, active: Boolean)

    /** PATCH /tickets/{id} : « Modifier l'affaire » — les seuls champs changés. */
    suspend fun enregistrer(id: Int, edition: EditionAffaire)

    /** PATCH /tickets/{id}/evolutions/{evol_id} : « Modifier la suite » ; rend ce que l'affaire n'en a pas suivi. */
    suspend fun corrigerSuite(id: Int, suite: Int, correction: CorrectionSuite): IssueCorrection?

    /** DELETE /tickets/{id}/evolutions/{evol_id} : effacer une entrée de la suite. */
    suspend fun supprimerSuite(id: Int, suite: Int)
}

/** Le service par le client généré, sur la session de la résidence — aucune route écrite à la main. */
class ServiceAffairesApi(private val session: SessionOuverte) :
    ServiceAffaires,
    ServicePerimetres by ServicePerimetresApi(session),
    ServiceLectures by ServiceLecturesApi(session),
    // 🚩 et 🙈 (#282) : offerts si l'installation les sert (`Installation.sertLePlancher`), que l'écran lit.
    ServiceSignalements by ServiceSignalementsApi(session),
    ServiceMasquages by ServiceMasquagesApi(session) {
    private suspend fun <R> tickets(requete: TicketsApi.() -> R): R =
        session.appeler { base, client -> TicketsApi(base, client).requete() }

    override suspend fun affaires() = tickets { getTickets().map(Affaire::depuis) }

    override suspend fun referentiels() = tickets { ReferentielsAffaires.depuis(getTicketsReferentiels()) }

    override suspend fun prestatairesDeLaResidence() = session.appeler { base, client ->
        PrestatairesApi(base, client).getPrestataires().filterNot { it.archivee == true }.map { PrestataireAffaire(it.id, it.nom) }
    }

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

    override suspend fun archiver(id: Int): Affaire = Affaire.depuis(tickets { patchTicketsTicketId(id, TicketUpdate(archiveManuel = true)) })

    override suspend fun creer(saisie: SaisieAffaire): Affaire {
        val envoyee = saisie.aEnvoyer
        val categorie = CategorieTicket.decode(envoyee.categorie) ?: throw ErreurService.Inattendue
        val corps = TicketCreate(
            titre = envoyee.titre.trim(), description = TexteDuSite.html(envoyee.description), categorie = categorie,
            urgente = envoyee.urgente, perimetreCible = envoyee.perimetre?.let { listOf(it) },
        )
        return Affaire.depuis(tickets { postTickets(corps) })
    }

    override suspend fun repondre(id: Int, texte: String) {
        tickets { postTicketsTicketIdMessages(id, MessageCreate(contenu = TexteDuSite.html(texte.trim()))) }
    }

    override suspend fun corrigerMessage(id: Int, message: Int, texte: String) {
        tickets { patchTicketsTicketIdMessagesMsgId(id, message, MessageUpdate(contenu = TexteDuSite.html(texte.trim()))) }
    }

    override suspend fun supprimerMessage(id: Int, message: Int) = tickets { deleteTicketsTicketIdMessagesMsgId(id, message) }

    override suspend fun ajouterSuite(id: Int, suite: SaisieSuite) {
        val nouveau = if (suite.changeEtat) StatutTicket.decode(suite.statut) ?: throw ErreurService.Inattendue else null
        val contenu = suite.texte.takeIf { it.isNotEmpty() }?.let(TexteDuSite::html)
        tickets { postTicketsTicketIdEvolutions(id, TicketEvolutionCreate(type = suite.type, contenu = contenu, nouveauStatut = nouveau)) }
    }

    override suspend fun regler(id: Int, option: OptionAffaire, active: Boolean) {
        val corps = if (option.cle == OptionAffaire.EPINGLE) TicketUpdate(epingle = active) else TicketUpdate(urgente = active)
        tickets { patchTicketsTicketId(id, corps) }
    }

    override suspend fun enregistrer(id: Int, edition: EditionAffaire) {
        val corps = TicketUpdate(
            titre = edition.titre, description = edition.descriptionHtml, categorie = edition.categorie,
            urgente = edition.urgente, perimetreCible = edition.perimetres, prestataireIds = edition.prestataires,
        )
        tickets { patchTicketsTicketId(id, corps) }
    }

    override suspend fun corrigerSuite(id: Int, suite: Int, correction: CorrectionSuite): IssueCorrection? {
        val statut = correction.suivi?.statut?.let { StatutTicket.decode(it) ?: throw ErreurService.Inattendue }
        val corps = TicketEvolutionUpdate(contenu = correction.contenuHtml, nouveauStatut = statut, type = correction.suivi?.type)
        return IssueCorrection.lue(tickets { patchTicketsTicketIdEvolutionsEvolId(id, suite, corps) })
    }

    override suspend fun supprimerSuite(id: Int, suite: Int) = tickets { deleteTicketsTicketIdEvolutionsEvolId(id, suite) }

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
