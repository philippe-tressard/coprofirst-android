// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun

import fr.coprofirst.app.session.ErreurService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La machine d'envoi commune (#324) : un envoi à la fois, le drapeau levé avant de partir et
 * rendu quoi qu'il arrive (succès, échec, écran quitté), l'erreur gardée jusqu'au succès —
 * ou effacée au départ, quand l'écran le demande.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EnvoiTest {
    private val refus = ErreurService.Refus("Le titre est requis", 422)

    @Test
    fun unSeulEnvoiALaFoisEtLeDrapeauSeLeveAvantDePartir() = runTest {
        val envoi = Envoi(this)
        val reponse = CompletableDeferred<Unit>()
        var appels = 0
        envoi.lancer {
            appels++
            reponse.await()
        }
        assertTrue("levé d'un seul tenant avec la garde", envoi.enCours)
        envoi.lancer { appels++ }
        runCurrent()
        assertEquals(1, appels)
        reponse.complete(Unit)
        runCurrent()
        assertFalse(envoi.enCours)
        assertNull(envoi.erreur)
    }

    @Test
    fun unEchecSeGardeSeDitPuisUnSuccesLEfface() = runTest {
        val envoi = Envoi(this)
        val dites = mutableListOf<ErreurService>()
        var suite = false
        envoi.lancer(echec = { dites += it }) {
            throw refus
        }
        runCurrent()
        assertEquals(refus, envoi.erreur)
        assertEquals(listOf<ErreurService>(refus), dites)
        assertFalse("rendu sur un échec", envoi.enCours)

        // Un second essai : l'erreur reste à l'écran pendant l'envoi, puis le succès l'efface.
        val reponse = CompletableDeferred<Unit>()
        envoi.lancer {
            reponse.await()
            suite = true
        }
        runCurrent()
        assertEquals(refus, envoi.erreur)
        reponse.complete(Unit)
        runCurrent()
        assertTrue(suite)
        assertNull(envoi.erreur)
    }

    @Test
    fun effacerRetireLErreurDesLeDepart() = runTest {
        val envoi = Envoi(this)
        envoi.lancer { throw refus }
        runCurrent()
        val reponse = CompletableDeferred<Unit>()
        envoi.lancer(effacer = true) { reponse.await() }
        assertNull(envoi.erreur)
        reponse.complete(Unit)
        runCurrent()
    }

    @Test
    fun lEcranQuitteRendLeDrapeauSansErreur() {
        val portee = TestScope()
        val envoi = Envoi(portee)
        envoi.lancer { CompletableDeferred<Unit>().await() }
        portee.runCurrent()
        assertTrue(envoi.enCours)
        portee.cancel()
        portee.runCurrent()
        assertFalse("rendu sur une annulation", envoi.enCours)
        assertNull(envoi.erreur)
    }
}
