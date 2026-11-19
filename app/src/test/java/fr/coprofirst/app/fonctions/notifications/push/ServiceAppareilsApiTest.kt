// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.notifications.push

import fr.coprofirst.app.session.AdresseInstallation
import fr.coprofirst.app.session.Installation
import fr.coprofirst.app.session.SessionOuverte
import fr.coprofirst.app.session.SessionsParHote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/** POST /appareils dit ce que l'appareil EST : un Android, servi par FCM (contrat 2.4.0). */
class ServiceAppareilsApiTest {
    private val serveur = MockWebServer().apply { start() }
    private val session = SessionOuverte(
        Installation("https://residence.exemple.test", "Les Glycines"),
        SessionsParHote(OkHttpClient()),
        Dispatchers.Unconfined,
        { _: AdresseInstallation -> serveur.url("/api").toString() },
    )

    @After
    fun arreter() = serveur.close()

    @Test
    fun lAppareilSeDitAndroid() = runTest {
        serveur.enqueue(MockResponse.Builder().code(201).addHeader("Content-Type", "application/json").body("{}").build())
        ServiceAppareilsApi.enregistrer(AppareilPush(JetonPush("jeton-fcm"), EnvironnementPush.PRODUCTION, "abonnement-1"), session)
        val requete = serveur.takeRequest()
        assertEquals("POST /api/appareils", "${requete.method} ${requete.url.encodedPath}")
        val corps = Json.parseToJsonElement(requete.body!!.utf8()).jsonObject
        assertEquals("fcm", corps["plateforme"]?.jsonPrimitive?.content)
        assertEquals("production", corps["environnement"]?.jsonPrimitive?.content)
        assertEquals("jeton-fcm", corps["jeton"]?.jsonPrimitive?.content)
        assertEquals("abonnement-1", corps["abonnement"]?.jsonPrimitive?.content)
    }
}
