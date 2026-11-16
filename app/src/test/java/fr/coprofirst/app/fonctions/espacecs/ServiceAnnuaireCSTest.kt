// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.espacecs

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * « Annuaire CS & Syndic », par le client GÉNÉRÉ, contre la résidence simulée : la lecture,
 * la réécriture ENTIÈRE (la composition enregistrée revient, fiches neuves numérotées et
 * noms servis) et les règles des gestes (un seul président, un seul principal, l'ordre).
 */
class ServiceAnnuaireCSTest {
    private val banc = ServeurCS()
    private val service = ServiceAnnuaireCSApi(banc.session)

    @After
    fun arreter() = banc.close()

    @Test
    fun leConseilEtLeSyndicSeLisent() = runTest {
        val conseil = service.conseil()
        assertEquals(2026, conseil.agAnnee)
        assertEquals("2026-06-12", conseil.agDate)
        assertEquals(listOf("Camille EXEMPLE", "Basile FICTIF"), conseil.membres.map { it.nomAffiche })
        assertEquals("2ème étage", conseil.membres[0].etageLibelle)
        assertNull("un étage vide se tait", conseil.membres[1].etageLibelle)
        assertTrue(conseil.membres[0].enCarte.estPresident)
        val syndic = service.syndic()
        assertFalse(syndic.nomDuContrat)
        assertEquals(listOf("01 99 00 00 01"), syndic.membres[0].telephones)
        assertTrue(syndic.membres[0].enCarte.estPrincipal)
    }

    /** Une fiche neuve : le serveur lui donne son numéro et son nom servi ; l'écran montre ce qui est retenu. */
    @Test
    fun enregistrerRendLaCompositionRetenue() = runTest {
        val conseil = service.conseil()
        val neuf = MembreConseil(prenom = "alix", nom = "nouveau")
        val retenu = service.enregistrer(conseil.copy(membres = conseil.membres + neuf))
        assertEquals("alix NOUVEAU", retenue(retenu.membres, null)?.nomAffiche)
        assertEquals(100, retenu.membres.last().id)
        val corps = banc.residence.recues.single { it.startsWith("PUT /api/admin/annuaire/cs") }
        assertTrue("une civilité par défaut part", corps.contains("\"genre\":\"${AnnuaireCS.civiliteParDefaut}\""))
        assertTrue("le lien à l'inscrit est conservé", corps.contains("\"user_id\":21"))
    }

    @Test
    fun lesNumerosPartentEnUnChamp() = runTest {
        val syndic = service.syndic()
        val modifie = syndic.membres[1].copy(telephones = listOf(" 01 99 00 00 02 ", "", "06 99 00 00 03"))
        val retenu = service.enregistrer(syndic.copy(membres = listOf(syndic.membres[0], modifie)))
        assertTrue(banc.residence.recues.any { it.contains("\"telephone\":\"01 99 00 00 02,06 99 00 00 03\"") })
        assertEquals(listOf("01 99 00 00 02", "06 99 00 00 03"), retenu.membres[1].telephones)
    }

    @Test
    fun leRapprochementSeLit() = runTest {
        val trouve = service.rapprochement("Exemple", localiser = true)
        assertEquals(31, trouve.userId)
        assertEquals("A", trouve.batimentNom)
        assertTrue(banc.residence.recues.any { it.startsWith("GET /api/admin/annuaire/rapprochement?") && it.contains("localiser=true") })
    }

    @Test
    fun lesReglesDesGestes() {
        val conseil = ConseilCS(membres = listOf(MembreConseil(estPresident = true), MembreConseil(), MembreConseil()))
        assertEquals(listOf(false, false, true), conseil.avecPresident(2).membres.map { it.estPresident })
        assertEquals(listOf(false, false, false), conseil.avecPresident(null).membres.map { it.estPresident })
        val syndic = SyndicCS(membres = listOf(MembreSyndic(id = 1), MembreSyndic(id = 2), MembreSyndic(id = 3)))
        assertEquals(listOf(true, false, false), syndic.avecPrincipal(0).membres.map { it.estPrincipal })
        assertEquals(listOf(2, 1, 3), syndic.deplace(0, 1).membres.map { it.id })
        assertEquals(listOf(1, 3, 2), syndic.deplace(2, -1).membres.map { it.id })
        assertEquals(syndic, syndic.deplace(0, -1))
        assertEquals(listOf(""), AnnuaireCS.champsTelephone(emptyList()))
        assertNull(AnnuaireCS.champTelephone(listOf(" ", "")))
        assertTrue(AnnuaireCS.nomme(" ", "Nom"))
        assertFalse(AnnuaireCS.nomme(null, " "))
        assertEquals("B", retenue(listOf("A", "B"), null))
        assertEquals("A", retenue(listOf("A", "B"), 0))
    }
}
