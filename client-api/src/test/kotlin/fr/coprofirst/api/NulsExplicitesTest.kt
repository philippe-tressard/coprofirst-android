// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.api

import fr.coprofirst.api.modeles.NotationUpdate
import fr.coprofirst.api.routes.PrestatairesApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
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
 * Effacer un champ par le client généré (coprofirst-android#209) : `NulsExplicites` écrit
 * `"ticket_id": null` dans le corps qui part vraiment, sans toucher au reste.
 */
class NulsExplicitesTest {
    private val serveur = MockWebServer()

    @Before fun demarrer() = serveur.start()

    @After fun arreter() = serveur.close()

    private fun envoyer(corps: NotationUpdate, champs: Set<String>): JsonObject {
        serveur.enqueue(MockResponse.Builder().code(500).build())
        runCatching {
            PrestatairesApi(serveur.url("/api").toString().trimEnd('/'), NulsExplicites(OkHttpClient(), champs))
                .patchPrestatairesNotationsNId(12, corps)
        }
        val requete = serveur.takeRequest()
        assertEquals("PATCH", requete.method)
        assertEquals("/api/prestataires/notations/12", requete.url.encodedPath)
        return Json.parseToJsonElement(requete.body!!.utf8()).jsonObject
    }

    @Test
    fun unChampAEffacerPartANull() {
        val envoye = envoyer(NotationUpdate(), setOf("ticket_id"))
        assertEquals(JsonNull, envoye["ticket_id"])
        assertEquals("le reste du corps est celui du client généré", setOf("ticket_id", "confirmer_ajout"), envoye.keys)
    }

    @Test
    fun unChampQuiPorteUneValeurLaGarde() {
        val envoye = envoyer(NotationUpdate(ticketId = 45), setOf("ticket_id"))
        assertEquals(45, envoye.getValue("ticket_id").jsonPrimitive.int)
    }

    @Test
    fun sansChampLeCorpsResteIntact() {
        val envoye = envoyer(NotationUpdate(commentaire = "Bien"), emptySet())
        assertEquals(setOf("commentaire", "confirmer_ajout"), envoye.keys)
    }
}
