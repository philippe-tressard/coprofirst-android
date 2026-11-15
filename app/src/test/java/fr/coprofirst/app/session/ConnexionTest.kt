// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.session

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ConnexionTest {
    private val serveur = MockWebServer().apply { start() }
    private val sessions = SessionsParHote(OkHttpClient())
    private val connexion = Connexion(sessions, Dispatchers.Unconfined) { serveur.url("/api").toString() }
    private val residence = Installation("https://residence.exemple.test", "Les Glycines")

    @After
    fun arreter() = serveur.close()

    private fun repondre(code: Int, vararg entetes: Pair<String, String>) {
        val reponse = MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body("{}")
        entetes.forEach { (nom, valeur) -> reponse.addHeader(nom, valeur) }
        serveur.enqueue(reponse.build())
    }

    private suspend fun echec(): ErreurService = try {
        connexion.connecter(residence, "a@exemple.test", "secret")
        error("connexion acceptée")
    } catch (erreur: ErreurService) {
        erreur
    }

    @Test
    fun laSessionVitEnCookiesDeLHoteEtSOublieALaDeconnexion() = runTest {
        repondre(200, "Set-Cookie" to "access_token=jeton; Path=/; HttpOnly")
        connexion.connecter(residence, " a@exemple.test ", "secret")
        val envoi = serveur.takeRequest()
        assertEquals("/api/auth/login", envoi.url.encodedPath)
        assertEquals("""{"email":"a@exemple.test","password":"secret"}""", envoi.body?.utf8())
        assertEquals("jeton", sessions.cookie(residence.adresse!!, "access_token"))
        // Une autre résidence n'a pas les cookies de la première.
        assertNull(sessions.cookie(AdresseInstallation.normaliser("autre.exemple.test")!!, "access_token"))
        repondre(200)
        connexion.deconnecter(residence)
        assertEquals("access_token=jeton", serveur.takeRequest().headers["Cookie"])
        assertNull(sessions.cookie(residence.adresse!!, "access_token"))
    }

    @Test
    fun identifiantsRefusesEtTropDEssais() = runTest {
        repondre(422)
        assertSame(ErreurService.IdentifiantsRefuses, echec())
        repondre(401)
        assertSame(ErreurService.IdentifiantsRefuses, echec())
        repondre(429)
        assertSame(ErreurService.TropDEssais, echec())
        repondre(500)
        assertSame(ErreurService.Inattendue, echec())
    }
}
