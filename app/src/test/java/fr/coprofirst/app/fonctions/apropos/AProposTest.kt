// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.apropos

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
import org.junit.Assert.assertNull
import org.junit.Test

/** « À propos » (#27) : la mention du pied, les deux routes publiques, les composants tiers. */
class AProposTest {
    private val serveur = MockWebServer().apply { start() }

    @After
    fun arreter() = serveur.close()

    @Test
    fun laMentionDeLAnneeSuitLeSite() {
        assertEquals("© 2026", AProposResidence.mentionAnnee(null, 2026))
        assertEquals("© 2026", AProposResidence.mentionAnnee(2026, 2026))
        assertEquals("© 2024–2026", AProposResidence.mentionAnnee(2024, 2026))
    }

    @Test
    fun lesClesServiesSontLuesEtUnTexteVideSeTait() {
        val lu = AProposResidence.depuis(
            mapOf(TextesDuPied.cleAnneeDebut to " 2025 ", TextesDuPied.cleTexte to "  "),
            mapOf("mentions_legales" to "<p>Éditeur</p>", "politique_confidentialite" to ""),
        )
        assertEquals(2025, lu.anneeDebut)
        assertNull(lu.texte)
        assertEquals("<p>Éditeur</p>", lu.mentionsLegales)
        assertNull(lu.confidentialite)
    }

    @Test
    fun laMentionAssembleLAnneeLeTexteEtLaVersion() {
        assertEquals("© 2026 · app 0.8.13", AProposResidence.mention(null, 2026, "0.8.13"))
        val lu = AProposResidence(anneeDebut = 2025, texte = "Syndicat des Glycines")
        assertEquals("© 2025–2026 · Syndicat des Glycines", AProposResidence.mention(lu, 2026, null))
    }

    @Test
    fun lesDeuxRoutesPubliquesSeLisentEnsemble() = runTest {
        // Les deux lectures partent ensemble : chaque réponse se sert selon sa route.
        serveur.dispatcher = object : mockwebserver3.Dispatcher() {
            override fun dispatch(request: mockwebserver3.RecordedRequest): MockResponse {
                val corps = if (request.url.encodedPath.endsWith("/config/legal")) {
                    """{"mentions_legales": "<p>Éditeur</p>", "politique_confidentialite": "<p>Droits</p>"}"""
                } else {
                    """{"site_nom": "Les Glycines", "pied_de_page_annee_debut": "2024"}"""
                }
                return MockResponse.Builder().code(200).addHeader("Content-Type", "application/json").body(corps).build()
            }
        }
        val service = ServiceAProposApi(
            SessionOuverte(
                Installation("https://residence.exemple.test", "Les Glycines"),
                SessionsParHote(OkHttpClient()),
                Dispatchers.Unconfined,
            ) { _: AdresseInstallation -> serveur.url("/api").toString() },
        )
        val lu = service.aPropos()
        assertEquals(AProposResidence(2024, null, "<p>Éditeur</p>", "<p>Droits</p>"), lu)
    }

    @Test
    fun leParagrapheLogicielLibreEchappeCeQuIlRecoit() {
        assertEquals("a &amp; b &lt;c&gt; &quot;d&quot;", echapper("a & b <c> \"d\""))
    }

    @Test
    fun laDeclarationGenereeSeLit() {
        val (okhttp) = ComposantsTiers.lire(
            """{"composants": [{"nom": "OkHttp", "licences": ["Apache-2.0", "MPL-2.0"], "role": "Le transport.",
               "modules": ["com.squareup.okhttp3:okhttp"], "textes": ["avis.txt", "Apache-2.0.txt", "MPL-2.0.txt"]}]}""",
        )
        assertEquals("OkHttp", okhttp.nom)
        assertEquals(listOf("Apache-2.0", "MPL-2.0"), okhttp.licences)
        assertEquals(listOf("avis.txt", "Apache-2.0.txt", "MPL-2.0.txt"), okhttp.textes)
    }
}
