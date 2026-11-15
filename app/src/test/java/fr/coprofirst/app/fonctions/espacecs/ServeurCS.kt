// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.espacecs

import fr.coprofirst.app.session.AdresseInstallation
import fr.coprofirst.app.session.Installation
import fr.coprofirst.app.session.SessionOuverte
import fr.coprofirst.app.session.SessionsParHote
import kotlinx.coroutines.Dispatchers
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient

/**
 * La résidence simulée de l'Espace CS servie sur un serveur local, et la session de l'app
 * qui lui parle par son vrai client — le banc des tests JVM des services.
 */
class ServeurCS : AutoCloseable {
    val residence = ResidenceSimuleeCS()
    private val serveur = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val chemin = request.url.encodedPath + request.url.encodedQuery?.let { "?$it" }.orEmpty()
                val (code, corps) = residence.repondre(request.method, chemin, request.body?.utf8().orEmpty())
                return MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(corps).build()
            }
        }
        start()
    }
    val session = SessionOuverte(
        Installation("https://residence.test", "Les Glycines"),
        SessionsParHote(OkHttpClient()),
        Dispatchers.Unconfined,
    ) { _: AdresseInstallation -> serveur.url("/api").toString() }

    override fun close() = serveur.close()
}
