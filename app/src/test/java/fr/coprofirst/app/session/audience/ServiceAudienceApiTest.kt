// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.session.audience

import fr.coprofirst.app.session.AdresseInstallation
import fr.coprofirst.app.session.Installation
import fr.coprofirst.app.session.SessionOuverte
import fr.coprofirst.app.session.SessionsParHote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/** Le lot part par le client généré : la route du contrat, le canal de l'app, rien d'autre. */
class ServiceAudienceApiTest {
    private val serveur = MockWebServer().apply { start() }
    private val session = SessionOuverte(
        Installation("https://glycines.exemple.test", "Les Glycines", versionContrat = "2.0.0"),
        SessionsParHote(OkHttpClient()),
        Dispatchers.Unconfined,
    ) { _: AdresseInstallation -> serveur.url("/api").toString() }

    @After
    fun arreter() = serveur.close()

    @Test
    fun leLotDitLeCanalAndroidEtRienDAutre() = runTest {
        serveur.enqueue(MockResponse.Builder().code(204).build())
        ServiceAudienceApi.envoyer(listOf("/tableau-de-bord", "/admin#telemetry"), session)
        val requete = serveur.takeRequest()
        assertEquals("POST", requete.method)
        assertEquals("/api/telemetry/collect", requete.url.encodedPath)
        val corps = Json.parseToJsonElement(requete.body!!.utf8()).jsonObject
        assertEquals(setOf("events", "canal"), corps.keys)
        assertEquals("android", corps["canal"]!!.jsonPrimitive.content)
        val evenements = corps["events"]!!.jsonArray.map { it as JsonObject }
        assertEquals(listOf("/tableau-de-bord", "/admin#telemetry"), evenements.map { it["page"]!!.jsonPrimitive.content })
        assertEquals(setOf("view"), evenements.map { it["action"]!!.jsonPrimitive.content }.toSet())
        // Ni identifiant d'appareil, ni modèle, ni détail : une page et une action.
        assertEquals(setOf(setOf("page", "action")), evenements.map { it.keys }.toSet())
    }
}
