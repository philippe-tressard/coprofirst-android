// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.api

import fr.coprofirst.api.infrastructure.Serializer
import fr.coprofirst.api.modeles.TicketUpdate
import fr.coprofirst.api.routes.TicketsApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Un corps PARTIEL n'émet que ce qu'il porte (#99) : le serveur applique un champ envoyé à
 * null (`model_fields_set`) — un « Archiver » qui écrivait tous les champs de
 * `TicketUpdate` à null effaçait les affaires liées et « Saisi pour ».
 */
class CorpsPartielTest {
    private val serveur = MockWebServer()

    @Before fun demarrer() = serveur.start()

    @After fun arreter() = serveur.close()

    @Test
    fun unChampNullNeSeSerialisePas() {
        val corps = Serializer.kotlinxSerializationJson.encodeToString(TicketUpdate.serializer(), TicketUpdate(archiveManuel = true))
        assertEquals("""{"archive_manuel":true}""", corps)
    }

    @Test
    fun unPatchPartielNEnvoieQueSonChamp() {
        // Le corps qui part VRAIMENT par la route générée, pas seulement le sérialiseur.
        serveur.enqueue(MockResponse.Builder().code(500).build())
        runCatching {
            TicketsApi(serveur.url("/api").toString().trimEnd('/'), OkHttpClient()).patchTicketsTicketId(45, TicketUpdate(archiveManuel = true))
        }
        val requete = serveur.takeRequest()
        assertEquals("PATCH", requete.method)
        val envoye: JsonObject = Json.parseToJsonElement(requete.body!!.utf8()).jsonObject
        assertEquals(setOf("archive_manuel"), envoye.keys)
        assertEquals(true, envoye.getValue("archive_manuel").jsonPrimitive.boolean)
    }

    @Test
    fun uneValeurEgaleAuDefautPartQuandMeme() {
        // `encodeDefaults` reste vrai : « épingler = non » doit partir, même si c'est le défaut.
        val corps = Serializer.kotlinxSerializationJson.encodeToString(TicketUpdate.serializer(), TicketUpdate(epingle = false))
        assertEquals("""{"epingle":false}""", corps)
    }
}
