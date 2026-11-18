// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.notifications.montre

import fr.coprofirst.app.session.AdresseInstallation
import fr.coprofirst.app.session.ClientHttp
import fr.coprofirst.app.session.Installation
import fr.coprofirst.app.session.SessionOuverte
import fr.coprofirst.app.session.SessionsParHote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/** Chaque geste de la liaison part par SA route du contrat (coprofirst#2771). */
class ServiceMontresApiTest {
    private val serveur = MockWebServer().apply { start() }
    private val session = SessionOuverte(
        Installation("https://residence.exemple.test", "Les Tilleuls"),
        SessionsParHote(ClientHttp.partage),
        Dispatchers.Unconfined,
        { _: AdresseInstallation -> serveur.url("/api").toString() },
    )
    private val emise = """{"id": 7, "plateforme": "wearos", "cree_le": "2026-11-17T10:00:00Z",
        "expire_le": "2026-12-17T10:00:00Z", "vu_le": null, "jeton": "brut"}"""

    @After
    fun arreter() = serveur.close()

    private fun repondre(code: Int, corps: String = "") = serveur.enqueue(
        MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(corps).build(),
    )

    @Test
    fun emettre_uneMontreWearOs() = runTest {
        repondre(201, emise)
        val jeton = ServiceMontresApi.emettre(session)
        val requete = serveur.takeRequest()
        assertEquals("POST /api/montres", "${requete.method} ${requete.url.encodedPath}")
        assertEquals("""{"plateforme":"wearos"}""", requete.body!!.utf8())
        assertEquals(7, jeton.id)
        assertEquals("brut", jeton.jeton)
        assertEquals(Instant.parse("2026-12-17T10:00:00Z"), jeton.expireLe)
    }

    @Test
    fun renouvelerEtRetirer_parLeurRoute() = runTest {
        repondre(200, emise)
        ServiceMontresApi.renouveler(7, session)
        assertEquals("POST /api/montres/7/renouveler", serveur.takeRequest().let { "${it.method} ${it.url.encodedPath}" })
        repondre(204)
        ServiceMontresApi.retirer(7, session)
        assertEquals("DELETE /api/montres/7", serveur.takeRequest().let { "${it.method} ${it.url.encodedPath}" })
    }
}
