// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.detail.sondage

import fr.coprofirst.app.R
import fr.coprofirst.app.commun.CibleSignalement
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Les règles de la fiche d'un sondage, celles du site et de l'iOS (`SondageTests`), sur les fiches de l'iOS. */
class ModeleSondageTest {
    private val fiches = FixturesSondage.detaillees().associateBy { it.id }

    @Test
    fun lesFichesSeLisentDuTypeGenere() {
        val fete = fiches.getValue(12)
        assertEquals(29, fete.totalVotes)
        assertTrue(fete.peutVoter)
        assertTrue(fete.options.last().champLibre)
        assertEquals(listOf("Le samedi 20 juin, après la brocante", "Plutôt un vendredi soir"), fete.options.last().reponsesLibres)
        assertEquals(listOf(31, 32, 33), fete.commentaires.map { it.id })
        assertEquals(FixturesSondage.MOI, fete.moi)
    }

    @Test
    fun desResultatsMasquesNeSontPasDesZeros() {
        val velos = fiches.getValue(11)
        assertFalse(velos.resultatsVisibles)
        assertTrue(velos.options.all { it.nbVotes == null })
        assertEquals(0, velos.totalVotes)
    }

    /**
     * Les résultats avant le vote (#218, 5Hostachy #2803, coprofirst-ios#257) : visibles avant
     * la clôture, ils passent au-dessus du formulaire et se disent provisoires ; masqués, rien
     * ne paraît ; clos, les barres sans la mention ; voté et encore ouvert, provisoires.
     */
    @Test
    fun lesResultatsVisiblesPassentAvantLeVoteEtSeDisentProvisoires() {
        val ouvert = fiches.getValue(12)
        assertTrue(ouvert.peutVoter && ouvert.resultatsAvantVote && ouvert.resultatsProvisoires)
        val masque = fiches.getValue(11)
        assertTrue(masque.peutVoter)
        assertFalse("masqués : aucun décompte avant la clôture", masque.resultatsAvantVote || masque.resultatsProvisoires)
        val clos = fiches.getValue(9)
        assertTrue(clos.resultatsVisibles)
        assertFalse("clos : les barres, sans la mention", clos.resultatsAvantVote || clos.resultatsProvisoires)
        val vote = ouvert.copy(monVote = 1)
        assertFalse(vote.resultatsAvantVote)
        assertTrue("voté, encore ouvert : provisoires", vote.resultatsProvisoires)
    }

    @Test
    fun unSondageClotureOuDejaVoteNeSeVotePlus() {
        val horaires = fiches.getValue(9)
        assertTrue(horaires.cloture)
        assertFalse(horaires.peutVoter)
        assertFalse(fiches.getValue(12).copy(monVote = 2).peutVoter)
    }

    @Test
    fun ceQuiManqueSeDitDansLOrdreDuSite() {
        assertEquals(R.string.sondage_choisir, VoteSondage().manque(champLibre = false))
        val choisi = VoteSondage().choisir(4)
        assertEquals(R.string.sondage_preciser_manque, choisi.manque(champLibre = true))
        assertEquals(R.string.sondage_charte_manque, choisi.manque(champLibre = false))
        val precise = choisi.copy(reponseLibre = "  Le 20 juin ")
        assertEquals(R.string.sondage_charte_manque, precise.manque(champLibre = true))
        assertNull(precise.copy(charteAcceptee = true).manque(champLibre = true))
    }

    @Test
    fun changerDeReponseVideLaPrecision() {
        val vote = VoteSondage(option = 4, reponseLibre = "Le 20 juin", commentaire = "Avec plaisir")
        assertEquals(vote, vote.choisir(4))
        assertEquals(VoteSondage(option = 1, commentaire = "Avec plaisir"), vote.choisir(1))
    }

    @Test
    fun leCorpsNEnvoiePasDeTexteVide() {
        assertNull(VoteSondage().corps())
        val corps = VoteSondage(option = 4, reponseLibre = " Le 20 juin ", commentaire = "  ").corps()!!
        assertEquals(4, corps.optionId)
        assertEquals("Le 20 juin", corps.reponseLibre)
        assertNull(corps.commentaire)
    }

    /** La part est SERVIE, arrondie par l'API (`options[].part`, #380) : lue telle quelle, absente si masquée. */
    @Test
    fun laPartSeLitTelleQuelleServie() {
        assertEquals(listOf(48, 31, 14, 7), fiches.getValue(12).options.map { it.part })
        assertEquals(listOf(71, 22, 7), fiches.getValue(9).options.map { it.part })
        assertTrue(fiches.getValue(11).options.all { it.part == null })
    }

    /** Le serveur simulé arrondit comme `part_en_pourcent` : demi au supérieur, en entiers, 0 sans vote. */
    @Test
    fun leServeurSimuleArrondiCommeLAPI() {
        val serveur = SondageSimule(attente = 0)
        assertEquals(0, serveur.partEnPourcent(0, 0))
        assertEquals(13, serveur.partEnPourcent(1, 8))
        assertEquals(67, serveur.partEnPourcent(2, 3))
        assertEquals(100, serveur.partEnPourcent(3, 3))
    }

    @Test
    fun leServeurSimuleCompteLeVoteEtSesTextes() = runTest {
        val serveur = SondageSimule(attente = 0)
        serveur.voter(VoteSondage(option = 4, reponseLibre = "Le 20 juin", commentaire = "Avec plaisir", charteAcceptee = true), 12)
        val apres = serveur.sondage(12)
        assertEquals(4, apres.monVote)
        assertEquals(30, apres.totalVotes)
        assertEquals(listOf(47, 30, 13, 10), apres.options.map { it.part })
        assertEquals("Le 20 juin", apres.options.last().reponsesLibres.last())
        assertEquals("Avec plaisir", apres.commentaires.last().contenu)
        serveur.signalerAuConseil(CibleSignalement.commentaire(32), "Hors sujet")
        assertEquals(listOf(CibleSignalement("commentaire", 32) to "Hors sujet"), serveur.signalements)
    }
}
