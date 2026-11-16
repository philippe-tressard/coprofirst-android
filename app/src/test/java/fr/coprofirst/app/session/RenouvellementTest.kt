// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.session

import fr.coprofirst.api.routes.ConfigApi
import fr.coprofirst.app.commun.pieces.fichierProtege
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LA porte du renouvellement (#13), comme `RenouvellementTests` de l'iOS : un seul
 * POST /auth/refresh à la fois par hôte — un jeton échangé deux fois fermerait toutes les
 * sessions du compte —, et la requête refusée se rejoue une fois.
 */
class RenouvellementTest {
    private val serveur = MockWebServer().apply { start() }
    private val gardees = SessionsGardeesEnMemoire()
    private val sessions = SessionsParHote(OkHttpClient(), gardees)
    private val base = { _: AdresseInstallation -> serveur.url("/api").toString() }
    private val residence = Installation("https://residence.exemple.test", "Les Glycines")
    private val session = SessionOuverte(residence, sessions, Dispatchers.Unconfined, base)

    @After
    fun arreter() = serveur.close()

    private fun repondre(code: Int, corps: String = "{}", vararg entetes: Pair<String, String>) {
        val r = MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(corps)
        entetes.forEach { (n, v) -> r.addHeader(n, v) }
        serveur.enqueue(r.build())
    }

    @Test
    fun desRenouvellementsConcurrentsNeFontQuUnSeulEchange() = runTest {
        val renouvellement = Renouvellement()
        val libere = CompletableDeferred<Unit>()
        var echanges = 0
        val appels = (1..3).map {
            async {
                renouvellement.renouveler("residence.exemple.test") {
                    echanges += 1
                    libere.await()
                    true
                }
            }
        }
        yield()
        libere.complete(Unit)
        assertEquals(listOf(true, true, true), appels.awaitAll())
        assertEquals(1, echanges)
    }

    @Test
    fun unRefusSePartageAvecCeuxQuiAttendaient() = runTest {
        val renouvellement = Renouvellement()
        val libere = CompletableDeferred<Unit>()
        var echanges = 0
        val appels = (1..2).map {
            async {
                renouvellement.renouveler("h") {
                    echanges += 1
                    libere.await()
                    false
                }
            }
        }
        yield()
        libere.complete(Unit)
        assertEquals(listOf(false, false), appels.awaitAll())
        assertEquals(1, echanges)
    }

    @Test
    fun uneRequetePartieAvantUnRenouvellementReussiSeRejoueSansRenouveler() = runTest {
        val renouvellement = Renouvellement()
        val avant = renouvellement.releve("h")
        assertTrue(renouvellement.renouveler("h") { true })
        var echanges = 0
        assertTrue(renouvellement.renouveler("h", depuis = avant) { echanges += 1; false })
        assertEquals(0, echanges)
    }

    @Test
    fun unRefus401SeRenouvelleEtSeRejoueUneFois() = runTest {
        repondre(401)
        repondre(200, "{}", "Set-Cookie" to "refresh_token=neuf; Path=/; HttpOnly")
        repondre(200, """{"site_nom": "Les Glycines"}""")
        assertEquals("Les Glycines", session.appeler { b, c -> ConfigApi(b, c).getConfig() }["site_nom"])
        assertEquals("/api/config", serveur.takeRequest().url.encodedPath)
        assertEquals("/api/auth/refresh", serveur.takeRequest().url.encodedPath)
        assertEquals("/api/config", serveur.takeRequest().url.encodedPath)
    }

    @Test
    fun unFichierProtegeSeRenouvelleAussi() = runTest {
        repondre(401)
        repondre(200, "{}", "Set-Cookie" to "refresh_token=neuf; Path=/; HttpOnly")
        serveur.enqueue(MockResponse.Builder().code(200).body("photo").build())
        assertEquals("photo", String(session.fichierProtege("/uploads/a.jpg")))
        assertEquals(3, serveur.requestCount)
    }

    @Test
    fun unRenouvellementRefuseDitLaSessionExpiree() = runTest {
        gardees.garder("residence.exemple.test", "r1")
        repondre(401)
        repondre(401, """{"detail": "Session expirée. Reconnectez-vous."}""")
        val erreur = runCatching { session.appeler { b, c -> ConfigApi(b, c).getConfig() } }.exceptionOrNull()
        assertEquals(ErreurService.SessionExpiree, erreur)
        assertEquals(2, serveur.requestCount)
        // Effacée de l'appareil (#201) : l'écran de retour ne propose plus l'empreinte.
        assertFalse(gardees.contient("residence.exemple.test"))
    }

    @Test
    fun leJetonQuiTourneRemplaceCeluiQueLAppareilGarde() = runTest {
        gardees.garder("residence.exemple.test", "ancien")
        repondre(401)
        repondre(200, "{}", "Set-Cookie" to "refresh_token=neuf; Path=/; HttpOnly")
        repondre(200, "{}")
        session.appeler { b, c -> ConfigApi(b, c).getConfig() }
        val lu = gardees.lire("residence.exemple.test") { c, _ -> c }
        assertEquals(LectureSession.Jeton("neuf"), lu)
    }

    @Test
    fun uneSessionNonGardeeNeSeGardePasAuRenouvellement() = runTest {
        repondre(401)
        repondre(200, "{}", "Set-Cookie" to "refresh_token=neuf; Path=/; HttpOnly")
        repondre(200, "{}")
        session.appeler { b, c -> ConfigApi(b, c).getConfig() }
        assertFalse(gardees.contient("residence.exemple.test"))
    }
}
