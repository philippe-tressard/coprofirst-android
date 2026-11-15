// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.faq

import fr.coprofirst.app.session.AdresseInstallation
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** La FAQ : ses routes, son regroupement, et le droit de la rédiger, SERVI (#37). */
class FaqTest {
    private val serveur = MockWebServer().apply { start() }
    private val service = ServiceFaqApi(
        SessionOuverte(
            Installation("https://residence.exemple.test", "Les Glycines"),
            SessionsParHote(OkHttpClient()),
            Dispatchers.Unconfined,
        ) { _: AdresseInstallation -> serveur.url("/api").toString() },
    )

    @After
    fun arreter() = serveur.close()

    private fun repondre(code: Int, corps: String) = serveur.enqueue(
        MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(corps).build(),
    )

    private fun question(id: Int, categorie: String, libelle: String) =
        """{"id": $id, "categorie": "$categorie", "question": "Q$id", "reponse": "<p>R$id</p>", "ordre": $id, "actif": true,
            "cree_le": "2026-11-01T10:00:00Z", "mis_a_jour_le": "2026-11-01T10:00:00Z", "categorie_libelle": "$libelle"}"""

    @Test
    fun laFaqSeLitEtSeRegroupeDansLOrdreDuServeur() = runTest {
        repondre(200, "[${question(1, "vie", "🏠 Vie en résidence")}, ${question(3, "acces", "🔑 Accès")}, " +
            "${question(2, "vie", "🏠 Vie en résidence")}, ${question(4, "", "")}]")
        repondre(200, """{"peut_rediger": true}""")
        val lue = ReglesFaq.lire(service, "Général")
        assertEquals("GET /api/faq", serveur.takeRequest().let { "${it.method} ${it.url.encodedPath}" })
        assertEquals("GET /api/faq/gestes", serveur.takeRequest().let { "${it.method} ${it.url.encodedPath}" })
        assertEquals(listOf("🏠 Vie en résidence", "🔑 Accès", "Général"), lue.categories.map { it.nom })
        assertEquals(listOf(1, 2), lue.categories[0].questions.map { it.id })
        assertTrue(lue.peutRediger)
    }

    @Test
    fun unDroitIllisibleVautNonEtLaFaqSeLitQuandMeme() = runTest {
        repondre(200, "[${question(1, "vie", "Vie")}]")
        repondre(500, "{}")
        val lue = ReglesFaq.lire(service, "Général")
        assertFalse(lue.peutRediger)
        assertEquals(1, lue.categories.single().questions.size)
    }

    @Test
    fun lesLiensSontSousLAdresseDeLaResidence() {
        val liens = LiensFaq.depuis("/faq", "https://residence.test/copro")
        assertEquals("https://residence.test/copro/faq", liens.faq)
        assertEquals("https://residence.test/copro/faq#faq-7", liens.question(7))
        assertEquals("https://residence.test/copro/manuel-utilisateur.html", liens.manuel)
    }
}
