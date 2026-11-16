// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.prestataires

import fr.coprofirst.api.modeles.NotationUpdate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** La saisie d'un avis et le choix de l'affaire (#209) : les règles du site, sur la JVM. */
class SaisieAvisTest {
    private val avant = AvisPrestataire(12, "neutre", "Délai un peu long.", creeLe = Instant.EPOCH, peutModifier = true, ticketId = 45)

    @Test
    fun lesRefusDuSiteDansLeurOrdre() {
        assertEquals(RefusAvis.EtatManquant, SaisieAvis().refus(ajoutRequis = false))
        assertEquals(RefusAvis.MauvaisSansCommentaire, SaisieAvis(etat = "mauvais", commentaire = "   ").refus(false))
        assertEquals(RefusAvis.AjoutNonConfirme, SaisieAvis(etat = "neutre", affaire = 52).refus(ajoutRequis = true))
        assertNull(SaisieAvis(etat = "neutre", affaire = 52, confirmeAjout = true).refus(ajoutRequis = true))
        assertNull("Neutre sans commentaire part", SaisieAvis(etat = "neutre").refus(false))
    }

    @Test
    fun lesEtatsSontCeuxDuContrat() {
        assertEquals(listOf("mauvais", "neutre", "excellent"), SaisieAvis.etats)
    }

    @Test
    fun laConfirmationNePartQueSiLAjoutEstRequis() {
        val saisie = SaisieAvis(etat = "excellent", affaire = 45, confirmeAjout = true)
        assertFalse(saisie.creation(1, contrat = 7, ajoutRequis = false).confirmerAjout == true)
        assertEquals(7, saisie.creation(1, contrat = 7, ajoutRequis = false).contratId)
        assertNull("un commentaire vide ne part pas", saisie.creation(1, null, false).commentaire)
    }

    @Test
    fun corrigerPartDeLAvisEtNEnvoieQueCeQuiChange() {
        val saisie = SaisieAvis.corrigeant(avant)
        assertEquals(SaisieAvis(etat = "neutre", commentaire = "Délai un peu long.", affaire = 45), saisie)
        val inchangee = saisie.correction(avant, ajoutRequis = false)
        assertEquals(NotationUpdate(confirmerAjout = false), inchangee.corps)
        assertFalse(inchangee.effacerAffaire)

        val etat = saisie.copy(etat = "excellent").correction(avant, false).corps
        assertEquals(NotationUpdate.Etat.EXCELLENT, etat.etat)
        assertNull(etat.commentaire)
        assertNull(etat.ticketId)
    }

    @Test
    fun unCommentaireVideAvecUnEtatPartEnChaineVide() {
        val corps = SaisieAvis.corrigeant(avant).copy(commentaire = "  ").correction(avant, false).corps
        assertEquals("", corps.commentaire)
    }

    @Test
    fun changerDAffaireOuLaRetirer() {
        val autre = SaisieAvis.corrigeant(avant).copy(affaire = 52, confirmeAjout = true).correction(avant, ajoutRequis = true)
        assertEquals(52, autre.corps.ticketId)
        assertEquals(true, autre.corps.confirmerAjout)
        assertFalse(autre.effacerAffaire)
        val sans = SaisieAvis.corrigeant(avant).copy(affaire = null).correction(avant, false)
        assertTrue("« sans affaire » : ticket_id null explicite", sans.effacerAffaire)
        assertFalse(
            "un avis déjà sans affaire n'efface rien",
            SaisieAvis.corrigeant(avant.copy(ticketId = null)).correction(avant.copy(ticketId = null), false).effacerAffaire,
        )
    }

    private val affaires = listOf(
        AffaireANoter(45, "A-0045", "Contrôle annuel de l'ascenseur", intervient = true),
        AffaireANoter(38, "A-0038", "Cabine bloquée", intervient = true),
        AffaireANoter(52, "A-0052", "Fuite en toiture", intervient = false),
        AffaireANoter(60, "A-0060", "Ascenseur : voyant", intervient = false),
    ) + (61..70).map { AffaireANoter(it, "A-00$it", "Affaire $it", intervient = false) }

    @Test
    fun sansRechercheDeuxGroupesBornes() {
        val (siennes, autres) = ChoixAffaire.groupes(affaires, "", emptyList(), minimum = 2)
        assertEquals(listOf(45, 38), siennes.map { it.id })
        assertEquals(ChoixAffaire.PAR_GROUPE, autres.size)
        assertEquals(52, autres.first().id)
    }

    @Test
    fun uneFrappeFiltreEtLaRechercheDetailleeAjoute() {
        val (siennes, autres) = ChoixAffaire.groupes(affaires, "ascenseur", trouvees = listOf(66), minimum = 2)
        assertEquals(listOf(45), siennes.map { it.id })
        assertEquals("le titre trouvé d'abord, puis la pertinence servie", listOf(60, 66), autres.map { it.id })
        assertEquals("un « # » devant le numéro ne compte pas", listOf(52), ChoixAffaire.groupes(affaires, "#A-0052", emptyList(), 2).second.map { it.id })
        assertEquals("sous le seuil, pas de recherche", 2, ChoixAffaire.groupes(affaires, "z", emptyList(), 2).first.size)
    }
}
