// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.notifications

import fr.coprofirst.app.session.AdresseInstallation
import fr.coprofirst.app.session.ErreurService
import fr.coprofirst.app.session.Installation
import fr.coprofirst.app.session.SessionOuverte
import fr.coprofirst.app.session.SessionsParHote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant

/** Chaque geste part par SA route du contrat, par le client généré. */
class ServiceNotificationsApiTest {
    private val serveur = MockWebServer().apply { start() }
    private val base = { _: AdresseInstallation -> serveur.url("/api").toString() }
    private val session = SessionOuverte(
        Installation("https://residence.exemple.test", "Les Glycines"),
        SessionsParHote(OkHttpClient()),
        Dispatchers.Unconfined,
        base,
    )
    private val service = ServiceNotificationsApi(session)

    @After
    fun arreter() = serveur.close()

    private fun repondre(code: Int, corps: String = "{}") = serveur.enqueue(
        MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(corps).build(),
    )

    private fun requete(): String = serveur.takeRequest().let { "${it.method} ${it.url.encodedPath}" }

    @Test
    fun laListeSeLitDuContrat() = runTest {
        repondre(
            200,
            """[{"id": 5, "type": "ticket", "titre": "Votre affaire avance", "corps": "<p>x</p>",
               "lien": null, "lue": false, "urgente": true, "cree_le": "2026-11-15T08:30:00Z"}]""",
        )
        val liste = service.notifications()
        assertEquals("GET /api/notifications", requete())
        assertEquals(
            listOf(NotificationRecue(5, "ticket", "Votre affaire avance", "<p>x</p>", false, true, Instant.parse("2026-11-15T08:30:00Z"))),
            liste,
        )
    }

    @Test
    fun chaqueGestePartParSaRoute() = runTest {
        val lue = """{"id": 7, "type": "t", "titre": "T", "corps": "", "lue": true, "urgente": false, "cree_le": "2026-11-15T08:30:00Z"}"""
        repondre(200, lue)
        service.marquerLue(7)
        assertEquals("PATCH /api/notifications/7/lue", requete())
        repondre(200, """{"marquees": 3}""")
        service.toutMarquerLu()
        assertEquals("POST /api/notifications/tout-marquer-lu", requete())
        repondre(204, "")
        service.supprimerNotification(7)
        assertEquals("DELETE /api/notifications/7", requete())
        repondre(200, """{"supprimees": 2}""")
        service.supprimerLues()
        assertEquals("POST /api/notifications/supprimer-lues", requete())
        repondre(200, """{"supprimees": 3}""")
        service.supprimerNotifications(setOf(9, 3, 4))
        val selection = serveur.takeRequest()
        assertEquals("/api/notifications/supprimer-selection", selection.url.encodedPath)
        assertEquals("les identifiants, dans l'ordre", """{"ids":[3,4,9]}""", selection.body?.utf8())
    }

    @Test
    fun unRefusDitLaPhraseDuServeur() = runTest {
        repondre(403, """{"detail": "Réservé à son destinataire."}""")
        try {
            service.supprimerNotification(1)
            fail("refusé")
        } catch (e: ErreurService) {
            assertEquals(ErreurService.Refus("Réservé à son destinataire.", 403), e)
        }
    }
}
