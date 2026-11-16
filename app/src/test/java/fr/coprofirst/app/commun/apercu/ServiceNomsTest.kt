// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.apercu

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
import org.junit.Test

/** Le nom d'une saisie, rendu par le serveur (POST /noms/affiche), par le client GÉNÉRÉ. */
class ServiceNomsTest {
    private val serveur = MockWebServer().apply { start() }
    private val service = ServiceNomsApi(
        SessionOuverte(Installation("https://residence.test", "Les Glycines"), SessionsParHote(OkHttpClient()), Dispatchers.Unconfined) {
            _: AdresseInstallation -> serveur.url("/api").toString()
        },
    )

    @After
    fun arreter() = serveur.close()

    @Test
    fun leNomVientDuServeur() = runTest {
        serveur.enqueue(
            MockResponse.Builder().code(200).addHeader("Content-Type", "application/json")
                .body("""{"nom_affiche": "Jean-Baptiste FORT"}""").build(),
        )
        assertEquals("Jean-Baptiste FORT", service.nomAffiche("jean-baptiste", "fort"))
        val requete = serveur.takeRequest()
        assertEquals("POST", requete.method)
        assertEquals("/api/noms/affiche", requete.url.encodedPath)
        assertEquals("""{"prenom":"jean-baptiste","nom":"fort"}""", requete.body?.utf8())
    }

    @Test
    fun unChampVideNePartPas() = runTest {
        serveur.enqueue(MockResponse.Builder().code(200).addHeader("Content-Type", "application/json").body("""{"nom_affiche": "FORT"}""").build())
        assertEquals("FORT", service.nomAffiche(null, "fort"))
        assertEquals("""{"nom":"fort"}""", serveur.takeRequest().body?.utf8())
    }
}
