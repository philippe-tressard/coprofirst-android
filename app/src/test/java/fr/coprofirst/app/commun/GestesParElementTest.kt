// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun

import fr.coprofirst.app.session.ErreurService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

/**
 * Le geste tenu par élément (#378) : la clé de la carte posée le temps du geste, rendue au succès
 * comme à l'échec ; la valeur et l'exception remontent telles quelles — ce que faisaient, à la
 * main, les quatre files qui l'emploient désormais.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GestesParElementTest {
    private val refus = ErreurService.Refus("Déjà traitée", 409)

    @Test
    fun laCleSePosePendantLeGesteEtSeRendAuSucces() = runTest {
        val gestes = gestesParElement<Int>()
        val reponse = CompletableDeferred<String>()
        var rendu: String? = null
        assertNull(gestes.enCours)
        launch { rendu = gestes.geste(7) { reponse.await() } }
        runCurrent()
        assertEquals("la carte 7 attend", 7, gestes.enCours)
        reponse.complete("Demande approuvée")
        runCurrent()
        assertNull("rendue au succès", gestes.enCours)
        assertEquals("la valeur remonte telle quelle", "Demande approuvée", rendu)
    }

    @Test
    fun laCleSeRendALEchecEtLExceptionRemonte() = runTest {
        val gestes = gestesParElement<Int>()
        try {
            gestes.geste(3) { throw refus }
            fail("l'exception doit remonter à l'écran")
        } catch (e: ErreurService) {
            assertSame(refus, e)
        }
        assertNull("rendue à l'échec", gestes.enCours)
    }

    @Test
    fun deuxGestesSuccessifsTiennentChacunLeurCle() = runTest {
        val gestes = gestesParElement<String>()
        val vues = mutableListOf<String?>()
        gestes.geste("a") { vues += gestes.enCours }
        gestes.geste("b") { vues += gestes.enCours }
        assertEquals(listOf<String?>("a", "b"), vues)
        assertNull(gestes.enCours)
    }
}
