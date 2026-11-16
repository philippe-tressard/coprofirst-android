// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.session

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Les abonnés au cycle d'une résidence (#150) : prévenus dans l'ordre, chacun isolé des autres. */
class AbonnesSessionTest {
    private val session = SessionOuverte(Installation("https://a.exemple.test", "A"), SessionsParHote(OkHttpClient()))
    private val journal = mutableListOf<String>()

    private fun abonne(nom: String, echec: Throwable? = null) = object : AbonneSession {
        override suspend fun residenceOuverte(session: SessionOuverte) {
            journal += "$nom ouverte"
            echec?.let { throw it }
        }

        override suspend fun residenceFermee(session: SessionOuverte) {
            journal += "$nom fermée"
            echec?.let { throw it }
        }
    }

    @Test
    fun lEchecDUnAbonneNArretePasLesSuivants() = runTest {
        val abonnes = AbonnesSession(listOf(abonne("push", ErreurService.Injoignable), abonne("montre")))
        abonnes.ouverte(session)
        abonnes.fermee(session)
        assertEquals(listOf("push ouverte", "montre ouverte", "push fermée", "montre fermée"), journal)
    }

    @Test
    fun uneAnnulationNeSAvalePas() = runTest {
        val abonnes = AbonnesSession(listOf(abonne("push", CancellationException("quitté")), abonne("montre")))
        assertThrows(CancellationException::class.java) { kotlinx.coroutines.runBlocking { abonnes.fermee(session) } }
        assertEquals(listOf("push fermée"), journal)
    }
}
