// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.montre.liaison

import fr.coprofirst.api.infrastructure.ApiClient
import fr.coprofirst.api.routes.NotificationsApi
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

/** La montre lit la cloche, et elle seule, avec son jeton en porteur (coprofirst#2771). */
class ClocheParJetonTest {
    private val serveur = MockWebServer().apply { start() }
    private val cloche = ClocheParJeton(serveur.url("/api").toString(), "jeton-de-montre")

    @After
    fun arreter() = serveur.close()

    private fun repondre(code: Int, corps: String) = serveur.enqueue(
        MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(corps).build(),
    )

    @Test
    fun laClocheSeLitAvecLeJetonEnPorteur() = runTest {
        repondre(200, """[{"id": 5, "type": "ticket_update", "titre": "Votre affaire avance", "corps": "<p>x</p>",
            "lien": "/tickets/12", "lue": false, "urgente": true, "cree_le": "2026-11-17T08:30:00Z"}]""")
        val alertes = cloche.lire()
        val requete = serveur.takeRequest()
        assertEquals("GET /api/notifications", "${requete.method} ${requete.url.encodedPath}")
        assertEquals("Bearer jeton-de-montre", requete.headers["Authorization"])
        assertEquals(listOf(5), alertes.map { it.id })
        assertEquals("/tickets/12", alertes.single().lien)
    }

    /**
     * #264 : le jeton se donne à l'instance du client, jamais à `ApiClient.accessToken`, global :
     * la requête suivante d'un autre client (la cloche du téléphone, par sa session) part sans lui.
     */
    @Test
    fun leJetonNeFuitPasVersLesAutresRequetes() = runTest {
        repondre(200, "[]")
        repondre(200, "[]")
        cloche.lire()
        assertNull(ApiClient.accessToken)
        NotificationsApi(serveur.url("/api").toString()).getNotifications()
        serveur.takeRequest()
        assertNull(serveur.takeRequest().headers["Authorization"])
    }

    @Test
    fun unJetonRefuse_seDit() = runTest {
        repondre(401, """{"detail": "Non authentifié"}""")
        try {
            cloche.lire()
            fail("401 attendu")
        } catch (_: JetonRefuse) {
            // La montre efface sa liaison et attend le téléphone.
        }
    }
}
