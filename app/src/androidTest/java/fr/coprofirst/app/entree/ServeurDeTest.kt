// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.entree

import fr.coprofirst.app.session.ClientHttp
import fr.coprofirst.app.session.Connexion
import fr.coprofirst.app.session.Decouverte
import fr.coprofirst.app.session.InstallationsConnues
import fr.coprofirst.app.session.PreuveHote
import fr.coprofirst.app.session.SessionsParHote
import fr.coprofirst.app.session.StockageResidences
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate

/**
 * Un serveur local EN HTTPS (le clair est refusé par le manifeste, comme en vrai) et le
 * parcours d'entrée qui lui parle — le client de l'app, qui fait confiance à son seul
 * certificat. Toute adresse saisie mène à lui.
 */
class ServeurDeTest : AutoCloseable {
    private val certificat = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
    private val serveurTls = HandshakeCertificates.Builder().heldCertificate(certificat).build()
    private val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(certificat.certificate).build()
    val serveur = MockWebServer().apply {
        useHttps(serveurTls.sslSocketFactory())
        start()
    }
    private val client = ClientHttp.partage.newBuilder()
        .sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager)
        .build()

    fun parcours(preuve: PreuveHote = PreuveHote.Declaree): ParcoursEntree {
        val base = { _: Any -> serveur.url("/api").toString() }
        val stockage = object : StockageResidences {
            private var valeur: String? = null
            override fun lire() = valeur
            override fun ecrire(valeur: String) { this.valeur = valeur }
        }
        return ParcoursEntree(
            Decouverte(client, baseApi = base),
            Connexion(SessionsParHote(client), baseApi = base),
            InstallationsConnues(stockage),
        ) { preuve }
    }

    fun residence(nom: String) = repondre(200, """{"site_nom": "$nom"}""")

    fun repondre(code: Int, corps: String = "{}") = serveur.enqueue(
        MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(corps).build(),
    )

    override fun close() = serveur.close()
}
