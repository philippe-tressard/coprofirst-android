// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.detail.sondage

import fr.coprofirst.api.modeles.ReponseCommunauteRead
import fr.coprofirst.app.commun.CibleSignalement
import fr.coprofirst.app.session.ErreurService
import kotlinx.coroutines.delay
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * La fiche d'un sondage côté SERVEUR, pour les tests et les aperçus, comme l'API simulée de
 * l'iOS (`ServiceSimule: ServiceSondage`) : les fiches de `FixturesSondage`, que les gestes
 * changent comme le serveur (`participation.py`) — le vote compte, sa précision rejoint les
 * réponses libres, son commentaire rejoint les commentaires.
 */
class SondageSimule(private val attente: Long = 150) : ServiceSondage {
    private val fiches = FixturesSondage.detaillees().toMutableList()

    /** Les signalements reçus. */
    val signalements = mutableListOf<Pair<CibleSignalement, String>>()

    override suspend fun sondage(id: Int): DetailSondage {
        delay(attente)
        return fiche(id)
    }

    override suspend fun voter(vote: VoteSondage, sondage: Int) {
        delay(attente)
        var fiche = fiche(sondage)
        if (fiche.cloture) throw ErreurService.Refus("Ce sondage est clôturé", 400)
        if (fiche.monVote != null) throw ErreurService.Refus("Vous avez déjà voté", 400)
        val choisie = fiche.options.firstOrNull { it.id == vote.option } ?: throw ErreurService.Refus("Option invalide", 400)
        val precision = VoteSondage.nettoye(vote.reponseLibre)
        val options = fiche.options.map { option ->
            if (option.id != choisie.id || !fiche.resultatsVisibles) return@map option
            option.copy(nbVotes = (option.nbVotes ?: 0) + 1, reponsesLibres = option.reponsesLibres + listOfNotNull(precision))
        }
        fiche = fiche.copy(monVote = choisie.id, options = options)
        VoteSondage.nettoye(vote.commentaire)?.let { fiche = fiche.copy(commentaires = fiche.commentaires + commentaire(it, fiche)) }
        ranger(fiche)
    }

    override suspend fun commenter(texte: String, sondage: Int) {
        delay(attente)
        val contenu = VoteSondage.nettoye(texte) ?: throw ErreurService.Refus("Le commentaire ne peut pas être vide", 400)
        val fiche = fiche(sondage)
        ranger(fiche.copy(commentaires = fiche.commentaires + commentaire(contenu, fiche)))
    }

    override suspend fun supprimerCommentaire(commentaire: Int, sondage: Int) {
        delay(attente)
        val fiche = fiche(sondage)
        if (fiche.commentaires.none { it.id == commentaire && it.peutSupprimer == true }) {
            throw ErreurService.Refus("Action non autorisée", 403)
        }
        ranger(fiche.copy(commentaires = fiche.commentaires.filterNot { it.id == commentaire }))
    }

    override suspend fun signalerAuConseil(cible: CibleSignalement, motif: String) {
        delay(attente)
        signalements += cible to motif
    }

    /** La fiche de cet identifiant ; un sondage inconnu prend la forme du premier, sous son numéro. */
    @Synchronized
    private fun fiche(id: Int): DetailSondage =
        fiches.firstOrNull { it.id == id } ?: fiches.first().copy(id = id).also { fiches += it }

    @Synchronized
    private fun ranger(fiche: DetailSondage) {
        fiches[fiches.indexOfFirst { it.id == fiche.id }] = fiche
    }

    /** Un commentaire du compte simulé, comme `commenter` le rend : il peut le retirer. */
    private fun commentaire(contenu: String, fiche: DetailSondage) = ReponseCommunauteRead(
        id = (fiche.commentaires.maxOfOrNull { it.id } ?: 100) + 1,
        contenu = contenu,
        creeLe = OffsetDateTime.now(ZoneOffset.UTC),
        auteurNom = "Camille Essai",
        auteurId = FixturesSondage.MOI,
        auteurBatiment = "Bât. B",
        auteurRole = "Résident",
        estCs = false,
        peutSupprimer = true,
    )
}
