// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.session

import fr.coprofirst.api.routes.AuthApi
import fr.coprofirst.api.routes.ConfigApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/** LA porte des requêtes d'une résidence ouverte : ses cookies, ses erreurs dites. */
class SessionOuverteTest {
    private val serveur = MockWebServer().apply { start() }
    private val sessions = SessionsParHote(OkHttpClient())
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

    private suspend fun echec(): ErreurService = try {
        session.appeler { b, c -> ConfigApi(b, c).getConfig() }
        error("acceptée")
    } catch (e: ErreurService) {
        e
    }

    @Test
    fun lesRequetesPortentLaSessionDeLHote() = runTest {
        repondre(200, "{}", "Set-Cookie" to "access_token=jeton; Path=/; HttpOnly")
        Connexion(sessions, Dispatchers.Unconfined, base).connecter(residence, "a@exemple.test", "x")
        serveur.takeRequest()
        repondre(200, """{"site_nom": "Les Glycines"}""")
        assertEquals("Les Glycines", session.appeler { b, c -> ConfigApi(b, c).getConfig() }["site_nom"])
        assertEquals("access_token=jeton", serveur.takeRequest().headers["Cookie"])
    }

    @Test
    fun chaqueEchecADitSaPhrase() = runTest {
        repondre(401)
        assertEquals(ErreurService.SessionExpiree, echec())
        repondre(404)
        assertEquals(ErreurService.Introuvable, echec())
        repondre(403, """{"detail": "Réservé au conseil syndical."}""")
        assertEquals(ErreurService.Refus("Réservé au conseil syndical.", 403), echec())
        repondre(422, """{"detail": [{"loc": ["body"], "msg": "x"}]}""")
        assertEquals(ErreurService.Inattendue, echec())
        repondre(500)
        assertEquals(ErreurService.Inattendue, echec())
    }

    @Test
    fun uneReponseRecueIllisibleNEstPasUnHoteEtranger() = runTest {
        repondre(200, "<html>")
        assertEquals(ErreurService.Inattendue, try {
            session.appeler { b, c -> AuthApi(b, c).getAuthMe() }
            error("acceptée")
        } catch (e: ErreurService) {
            e
        })
    }
}
