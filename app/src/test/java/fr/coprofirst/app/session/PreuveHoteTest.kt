// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.session

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class PreuveHoteTest {
    private val app = PreuveHote.IDENTIFIANT_APP
    private val serveur = MockWebServer().apply { start() }
    private val adresse = AdresseInstallation.normaliser("residence.exemple.test")!!

    @After
    fun arreter() = serveur.close()

    @Test
    fun jugement() {
        assertEquals(PreuveHote.Declaree, PreuveHote.juger(200, """{"applinks":{"details":[{"appIDs":["$app"]}]}}"""))
        assertEquals(PreuveHote.Declaree, PreuveHote.juger(200, """{"applinks":{"details":[{"appID":"$app"}]}}"""))
        assertEquals(PreuveHote.Declaree, PreuveHote.juger(200, """{"webcredentials":{"apps":["$app"]}}"""))
        assertEquals(PreuveHote.SansLApp, PreuveHote.juger(200, """{"applinks":{"details":[{"appIDs":["AUTRE1234.fr.coprofirst.autre"]}]}}"""))
        assertEquals(PreuveHote.Absente, PreuveHote.juger(404, """{"webcredentials":{"apps":["$app"]}}"""))
        assertEquals(PreuveHote.Absente, PreuveHote.juger(200, "<html>"))
        assertEquals(PreuveHote.Absente, PreuveHote.juger(200, "x".repeat(PreuveHote.PLAFOND + 1)))
    }

    private suspend fun lire() =
        PreuveHote.lire(adresse, ClientHttp.partage, Dispatchers.Unconfined, serveur.url("/").toString().trimEnd('/'))

    @Test
    fun luALaBonneAdresseSansRedirection() = runTest {
        serveur.enqueue(MockResponse.Builder().code(200).body("""{"webcredentials":{"apps":["$app"]}}""").build())
        assertEquals(PreuveHote.Declaree, lire())
        assertEquals("/.well-known/apple-app-site-association", serveur.takeRequest().url.encodedPath)
        serveur.enqueue(MockResponse.Builder().code(301).addHeader("Location", "https://ailleurs.test/x").build())
        assertEquals(PreuveHote.Absente, lire())
        assertEquals(2, serveur.requestCount)
    }

    @Test
    fun hoteInjoignableSansPreuve() = runTest {
        serveur.close()
        assertEquals(PreuveHote.Absente, lire())
    }
}
