// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.fil

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
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Le fil part par SA route du contrat (GET /flux), par le client généré, et ses erreurs se disent. */
class ServiceFilApiTest {
    private val serveur = MockWebServer().apply { start() }
    private val base = { _: AdresseInstallation -> serveur.url("/api").toString() }
    private val session = SessionOuverte(
        Installation("https://residence.example.com", "Les Glycines"),
        SessionsParHote(OkHttpClient()),
        Dispatchers.Unconfined,
        base,
    )
    private val service = ServiceFilApi(session)

    @After
    fun arreter() = serveur.close()

    private fun repondre(code: Int, corps: String) = serveur.enqueue(
        MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(corps).build(),
    )

    @Test
    fun leFilSeLitDuContrat() = runTest {
        repondre(
            200,
            """{"delai_recent_jours": 7, "items": [
                 {"type": "ticket_ouvert", "id": "41", "date": "2026-11-15T08:30:00Z", "titre": "Fuite au parking",
                  "detail": "<p>Niveau -1</p>", "badges": ["urgent"], "icon": "", "lien": "/tickets/41",
                  "meta": {"ticket_id": 41, "affaire": {"natures": ["activite"]}}, "me_concerne": true},
                 {"type": "point_conseil", "id": "2", "date": "2026-11-14T17:00:00Z", "titre": "Point avec le syndic"}
               ], "sante": null}""",
        )
        val fil = service.fil()
        assertEquals("GET /api/flux", serveur.takeRequest().let { "${it.method} ${it.url.encodedPath}" })
        assertEquals(listOf("ticket_ouvert:41", "point_conseil:2"), fil.map { it.id })
        assertTrue(fil.first().urgent)
        assertEquals(CibleDuFil.Affaire(41), fil.first().cible)
        assertEquals(CibleDuFil.Resume(null), fil.last().cible)
    }

    @Test
    fun unFilSansElementEstVide() = runTest {
        repondre(200, """{"delai_recent_jours": 7}""")
        assertEquals(emptyList<ElementDuFil>(), service.fil())
    }

    @Test
    fun uneSessionExpireeSeDit() = runTest {
        repondre(401, "{}")
        try {
            service.fil()
            fail("refusé")
        } catch (e: ErreurService) {
            assertEquals(ErreurService.SessionExpiree, e)
        }
    }
}
