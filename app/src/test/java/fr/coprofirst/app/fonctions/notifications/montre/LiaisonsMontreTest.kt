// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.notifications.montre

import fr.coprofirst.app.commun.ReglagesEnMemoire
import fr.coprofirst.app.session.ErreurService
import fr.coprofirst.app.session.Installation
import fr.coprofirst.app.session.SessionOuverte
import fr.coprofirst.app.session.SessionsParHote
import fr.coprofirst.app.session.ClientHttp
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class LiaisonsMontreTest {
    private val adresse = "https://residence.exemple.test"
    private val session = SessionOuverte(Installation(adresse, "Les Tilleuls"), SessionsParHote(ClientHttp.partage))
    private val expire = Instant.parse("2026-12-17T10:00:00Z")

    private class Canal(var jointe: Boolean = true) : CanalMontre {
        val transmis = mutableListOf<MessageLiaison>()
        override suspend fun montreJointe() = jointe
        override suspend fun transmettre(message: MessageLiaison) { transmis += message }
    }

    private inner class Service(var renouvelable: Boolean = true, var retraitEchoue: Boolean = false) : ServiceMontres {
        val gestes = mutableListOf<String>()
        override suspend fun emettre(session: SessionOuverte) = JetonDeMontre(7, "neuf", expire).also { gestes += "emettre" }
        override suspend fun renouveler(id: Int, session: SessionOuverte): JetonDeMontre {
            gestes += "renouveler $id"
            if (!renouvelable) throw ErreurService.Introuvable
            return JetonDeMontre(id, "renouvele", expire)
        }
        override suspend fun retirer(id: Int, session: SessionOuverte) {
            gestes += "retirer $id"
            if (retraitEchoue) throw ErreurService.SessionExpiree
        }
    }

    private val reglages = ReglagesEnMemoire()
    private val gardees = MontresGardees(reglages)
    private val canal = Canal()
    private val service = Service()
    private fun liaisons(actif: Boolean = true) = LiaisonsMontre({ canal }, service, gardees, actif)

    @Test
    fun eteinte_rienNePart() = runTest {
        liaisons(actif = false).residenceOuverte(session)
        gardees.garder(adresse, 3)
        liaisons(actif = false).residenceFermee(session)
        assertEquals(emptyList<String>(), service.gestes)
        assertEquals(emptyList<MessageLiaison>(), canal.transmis)
    }

    @Test
    fun eteinte_leCanalNEstMemePasCree() = runTest {
        var cree = false
        val eteinte = LiaisonsMontre({ cree = true; canal }, service, gardees, actif = false)
        eteinte.residenceOuverte(session)
        eteinte.residenceFermee(session)
        assertEquals(false, cree)
    }

    @Test
    fun uneSessionNeuvePourLaMemeResidence_laMemeMontre() = runTest {
        liaisons().residenceOuverte(session)
        // Le parcours crée une SessionOuverte NEUVE à chaque geste : seule l'adresse compte.
        val autre = SessionOuverte(Installation(adresse, "Les Tilleuls"), SessionsParHote(ClientHttp.partage))
        liaisons().residenceFermee(autre)
        assertEquals(listOf("emettre", "retirer 7"), service.gestes)
    }

    @Test
    fun sansMontreJointe_rienNeSeDemande() = runTest {
        canal.jointe = false
        liaisons().residenceOuverte(session)
        assertEquals(emptyList<String>(), service.gestes)
    }

    @Test
    fun premiereOuverture_unJetonNeufPartAvecLaResidence() = runTest {
        liaisons().residenceOuverte(session)
        assertEquals(listOf("emettre"), service.gestes)
        assertEquals(7, gardees.id(adresse))
        assertEquals(listOf(MessageLiaison.Liaison(adresse, "Les Tilleuls", "neuf", expire.toString())), canal.transmis)
        // L'identifiant seul se garde, jamais le jeton.
        assertEquals(mapOf("montre:$adresse" to "7"), reglages.valeurs)
    }

    @Test
    fun montreDejaReliee_sonJetonSeRenouvelle() = runTest {
        gardees.garder(adresse, 3)
        liaisons().residenceOuverte(session)
        assertEquals(listOf("renouveler 3"), service.gestes)
        assertEquals("renouvele", (canal.transmis.single() as MessageLiaison.Liaison).jeton)
    }

    @Test
    fun montreInconnueDuServeur_unJetonNeufLaRemplace() = runTest {
        gardees.garder(adresse, 3)
        service.renouvelable = false
        liaisons().residenceOuverte(session)
        assertEquals(listOf("renouveler 3", "emettre"), service.gestes)
        assertEquals(7, gardees.id(adresse))
    }

    @Test
    fun fermeture_retireDuServeurEtEffaceLaMontre() = runTest {
        gardees.garder(adresse, 3)
        liaisons().residenceFermee(session)
        assertEquals(listOf("retirer 3"), service.gestes)
        assertEquals(listOf(MessageLiaison.Effacement(adresse)), canal.transmis)
        assertNull(gardees.id(adresse))
    }

    @Test
    fun fermeture_surSessionRefusee_laMontreSEffaceQuandMeme() = runTest {
        gardees.garder(adresse, 3)
        service.retraitEchoue = true
        liaisons().residenceFermee(session)
        assertEquals(listOf(MessageLiaison.Effacement(adresse)), canal.transmis)
        assertNull(gardees.id(adresse))
    }

    @Test
    fun fermetureSansMontreReliee_rienAFaire() = runTest {
        liaisons().residenceFermee(session)
        assertEquals(emptyList<String>(), service.gestes)
        assertEquals(emptyList<MessageLiaison>(), canal.transmis)
    }
}
