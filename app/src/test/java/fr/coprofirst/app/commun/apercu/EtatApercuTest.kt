// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.apercu

import fr.coprofirst.app.session.ErreurService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** L'aperçu servi d'une saisie (`EtatApercu` de l'iOS) : délai, réponse périmée, pas de redemande, échec tu. */
class EtatApercuTest {
    private val demandees = mutableListOf<String>()

    @Test
    fun uneRafaleDeSaisiesFaitUneSeuleDemandeApresLeDelai() = runTest {
        val etat = EtatApercu<String, String>()
        val demander: suspend (String) -> String = { demandees += it; "lieu de $it" }
        etat.saisie("a", this, demander)
        advanceTimeBy(EtatApercu.DELAI_MS / 2)
        etat.saisie("b", this, demander)
        advanceTimeBy(EtatApercu.DELAI_MS / 2)
        etat.saisie("c", this, demander)
        assertNull("rien avant la première réponse", etat.valeur)
        advanceUntilIdle()
        assertEquals(listOf("c"), demandees)
        assertEquals("lieu de c", etat.valeur)
    }

    @Test
    fun uneReponsePerimeeNeRemplacePasLaValeurEtNeSeRedemandePas() = runTest {
        val etat = EtatApercu<String, String>()
        val lente = CompletableDeferred<String>()
        etat.saisie("a", this) { demandees += it; lente.await() }
        advanceTimeBy(EtatApercu.DELAI_MS + 1)
        runCurrent()
        // La demande de « a » est partie ; la saisie passe à « b », servie aussitôt.
        etat.saisie("b", this) { demandees += it; "B" }
        advanceUntilIdle()
        assertEquals("B", etat.valeur)
        lente.complete("A")
        advanceUntilIdle()
        assertEquals("la réponse de « a » arrive trop tard", "B", etat.valeur)
    }

    @Test
    fun uneValeurDejaServieNeSeRedemandePas() = runTest {
        val etat = EtatApercu("a" to "connue")
        assertEquals("connue", etat.valeur)
        val demander: suspend (String) -> String = { demandees += it; it.uppercase() }
        etat.saisie("a", this, demander)
        etat.saisie("b", this, demander)
        advanceUntilIdle()
        etat.saisie("a", this, demander)
        advanceUntilIdle()
        assertEquals(listOf("b"), demandees)
        assertEquals("connue", etat.valeur)
        etat.saisie("b", this, demander)
        etat.saisie("b", this, demander)
        advanceUntilIdle()
        assertEquals(listOf("b"), demandees)
        assertEquals("B", etat.valeur)
    }

    @Test
    fun unEchecSeTaitEtLaDerniereValeurReste() = runTest {
        val etat = EtatApercu<String, String>()
        etat.saisie("a", this) { "A" }
        advanceUntilIdle()
        etat.saisie("b", this) { throw ErreurService.Injoignable }
        advanceUntilIdle()
        assertEquals("A", etat.valeur)
        // La même saisie peut repartir : l'échec n'est pas retenu comme une réponse.
        etat.saisie("b", this) { "B" }
        advanceUntilIdle()
        assertEquals("B", etat.valeur)
    }
}
