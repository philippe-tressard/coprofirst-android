// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.entree

import androidx.test.ext.junit.runners.AndroidJUnit4
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * La fermeture du serveur de test (#251) : une requête restée sans réponse posée reçoit la
 * sienne AVANT que le serveur ne ferme ses sockets. Sans cela, `MockWebServer.close` réveille
 * le fil qui la sert après avoir libéré son socket TLS, et Conscrypt fait tomber tout le
 * processus de test (`ssl == null`) — le job « Interface » avec lui.
 */
@RunWith(AndroidJUnit4::class)
class ServeurDeTestTest {
    @Test
    fun uneRequeteEnAttenteRecoitSaReponseAvantLaFermeture() {
        val serveur = ServeurDeTest()
        val issue = CompletableFuture<String>()
        serveur.client.newCall(Request.Builder().url(serveur.serveur.url("/api/auth/me")).build()).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) =
                response.use { issue.complete("${it.code} ${it.header("Content-Type")}") }.let { }

            override fun onFailure(call: Call, e: IOException) {
                issue.complete("échec : $e")
            }
        })
        val limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (serveur.requetesEnAttente() == 0 && System.nanoTime() < limite) Thread.sleep(20)
        assertEquals("la requête attend dans la file", 1, serveur.requetesEnAttente())

        serveur.close()

        // La réponse de ServeurDeTest (JSON), pas la « lettre morte » de MockWebServer, ni un socket coupé.
        assertEquals("503 application/json", issue.get(5, TimeUnit.SECONDS))
        assertEquals(0, serveur.requetesEnAttente())
    }
}
