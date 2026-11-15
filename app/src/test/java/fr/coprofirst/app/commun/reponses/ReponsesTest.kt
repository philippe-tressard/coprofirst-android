// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.reponses

import fr.coprofirst.api.modeles.ReponseCommunauteRead
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime

/** Les droits d'une réponse sont SERVIS (`peut_supprimer`) ou ceux du site (signaler les autres). */
class ReponsesTest {
    private fun reponse(auteur: Int?, peutSupprimer: Boolean? = false, nom: String = "Voisin du bât. A", cs: Boolean? = false) =
        ReponseCommunauteRead(
            id = 32, contenu = "Je préfère le 6.", creeLe = OffsetDateTime.parse("2026-11-09T18:30:00Z"),
            auteurNom = nom, auteurId = auteur, estCs = cs, peutSupprimer = peutSupprimer,
        )

    @Test
    fun supprimerSuitLeDroitServi() {
        assertTrue(Reponses.peutSupprimer(reponse(5, peutSupprimer = true)))
        assertFalse(Reponses.peutSupprimer(reponse(7, peutSupprimer = false)))
        assertFalse(Reponses.peutSupprimer(reponse(7, peutSupprimer = null)))
    }

    @Test
    fun onNeSignaleQueLesReponsesDesAutres() {
        assertTrue(Reponses.peutSignaler(reponse(5), moi = 7))
        assertFalse(Reponses.peutSignaler(reponse(7), moi = 7))
        // Le compte illisible : « Signaler » partout, le serveur juge.
        assertTrue(Reponses.peutSignaler(reponse(5), moi = null))
    }

    @Test
    fun leConseilEtLAuteurSeLisentDuServi() {
        assertTrue(Reponses.estCs(reponse(1, cs = true)))
        assertFalse(Reponses.estCs(reponse(1, cs = null)))
        assertEquals("Voisin du bât. A", Reponses.auteur(reponse(5)))
        assertNull(Reponses.auteur(reponse(5, nom = "  ")))
    }

    @Test
    fun unTexteVideNePartPas() {
        assertNull(Reponses.aPublier("   \n "))
        assertEquals("Je viendrai", Reponses.aPublier("  Je viendrai \n"))
    }

    @Test
    fun lesReperesSontCeuxDeLIOS() {
        assertEquals("reponse.33.supprimer", ReperesReponses.supprimer(33))
        assertEquals("reponse.32.signaler", ReperesReponses.signaler(32))
        assertEquals("reponses.confirmerSuppression", ReperesReponses.CONFIRMER_SUPPRESSION)
    }
}
