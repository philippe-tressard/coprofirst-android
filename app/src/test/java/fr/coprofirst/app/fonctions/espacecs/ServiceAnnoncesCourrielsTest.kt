// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.espacecs

import fr.coprofirst.app.session.ErreurService
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Les Annonces Hall et les Courriels affaires, par le client GÉNÉRÉ, contre la résidence simulée. */
class ServiceAnnoncesCourrielsTest {
    private val banc = ServeurCS()
    private val annonces = ServiceAnnoncesHallApi(banc.session)
    private val courriels = ServiceCourrielsApi(banc.session)

    @After
    fun arreter() = banc.close()

    @Test
    fun leJournalSeLitEtSesVerdictsSontCeuxDuSite() = runTest {
        val journal = courriels.journal()
        assertEquals(50, journal.limite)
        assertEquals(listOf(41, 40, 39, 38), journal.messages.map { it.id })
        assertEquals("TK-A00012", journal.messages[0].affaire)
        assertEquals(12, journal.messages[0].ticketId)
        assertNull(journal.messages[2].envoyeLe)
        assertEquals("Ajouté au fil", journal.messages[0].verdict.libelle)
        assertEquals("badge-green", journal.messages[0].verdict.badge)
        assertEquals("badge-orange", journal.messages[1].verdict.badge)
        // Un verdict que l'app ne connaît pas s'affiche brut : un libellé manquant doit se voir.
        assertEquals("nouveau_verdict", journal.messages[0].copy(decision = "nouveau_verdict").verdict.libelle)
    }

    @Test
    fun lesAffichesActivesPuisArchivees() = runTest {
        val toutes = annonces.toutes()
        assertEquals(listOf(5, 2), toutes.map { it.id })
        assertFalse(toutes[0].archivee)
        assertTrue(toutes[1].archivee)
        assertTrue(toutes[1].peutRestaurer)
        assertEquals("Bât. A", toutes[0].lieu)
        assertEquals(listOf("Alix Exemple", "Basile Fictif"), toutes[0].destinataires)
        assertTrue(banc.residence.recues.any { it.startsWith("GET /api/annonces-hall?archivees=true") })
    }

    /** La saisie part en HTML de l'éditeur, sans périmètre (le serveur pose le défaut), les envois décochés par défaut. */
    @Test
    fun creerEnvoieLaSaisie() = runTest {
        val saisie = SaisieAnnonceHall(titre = "  Ascenseur à l'arrêt ", message = "Lundi.\nMerci.", format = "a5", envoyerCS = true)
        assertTrue(saisie.complete)
        val creee = annonces.creer(saisie)
        assertEquals("Ascenseur à l'arrêt", creee.titre)
        val corps = banc.residence.recues.single { it.startsWith("POST /api/annonces-hall ") }
        assertTrue(corps, corps.contains("\"titre\":\"Ascenseur à l'arrêt\""))
        assertTrue(corps, corps.contains("\"format_demande\":\"a5\""))
        assertTrue(corps, corps.contains("\"envoyer_cs\":true"))
        assertTrue(corps, corps.contains("<p>"))
        // Aucun périmètre : le champ ne part pas (un null ne s'envoie plus, #99) — le serveur pose le défaut.
        assertFalse(corps, corps.contains("perimetre_cible"))
        assertFalse(SaisieAnnonceHall(titre = "x", message = "  ").complete)
    }

    @Test
    fun unRefusDitLaPhraseDuServeur() = runTest {
        try {
            annonces.creer(SaisieAnnonceHall(titre = " ", message = "x"))
            fail("le serveur refuse un titre vide")
        } catch (e: ErreurService.Refus) {
            assertEquals("Le titre est requis", e.phraseServie)
        }
    }

    @Test
    fun lesGestesPartentSurLeursRoutes() = runTest {
        annonces.renvoyer(5)
        annonces.archiver(5, archivee = true)
        annonces.supprimer(2)
        val recues = banc.residence.recues
        assertTrue(recues.any { it.startsWith("POST /api/annonces-hall/5/renvoyer-email") })
        assertTrue(recues.any { it.startsWith("PATCH /api/annonces-hall/5") && it.contains("\"archivee\":true") })
        assertTrue(recues.any { it.startsWith("DELETE /api/annonces-hall/2") })
    }
}
