// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.entree

import fr.coprofirst.app.commun.Apparence
import fr.coprofirst.app.commun.StockageApparence
import fr.coprofirst.app.session.ClientHttp
import fr.coprofirst.app.session.Connexion
import fr.coprofirst.app.session.Decouverte
import fr.coprofirst.app.session.InstallationsConnues
import fr.coprofirst.app.session.PreuveHote
import fr.coprofirst.app.session.SessionsParHote
import fr.coprofirst.app.session.StockageResidences
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.QueueDispatcher
import mockwebserver3.RecordedRequest
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import java.util.concurrent.ConcurrentHashMap

/**
 * Un serveur local EN HTTPS (le clair est refusé par le manifeste, comme en vrai) et le
 * parcours d'entrée qui lui parle — le client de l'app, qui fait confiance à son seul
 * certificat. Toute adresse saisie mène à lui.
 *
 * GET /flux (le fil, la première page d'une résidence ouverte) se sert HORS de la file, depuis
 * `flux` : il part en même temps que GET /auth/me, et prendrait sinon la réponse de l'autre.
 * De même toute route posée dans `fixes` (chemin → corps, en 200) : la fiche d'un sondage, lue
 * avec GET /auth/me.
 */
class ServeurDeTest : AutoCloseable {
    private val certificat = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
    private val serveurTls = HandshakeCertificates.Builder().heldCertificate(certificat).build()
    private val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(certificat.certificate).build()
    /** Ce que sert GET /flux : un fil vide, sauf si le test en pose un. */
    @Volatile
    var flux: String = FIL_VIDE

    /** Les routes servies hors de la file, par chemin (`/api/sondages/3`). */
    val fixes = ConcurrentHashMap<String, String>()
    val serveur = MockWebServer().apply {
        dispatcher = object : QueueDispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val chemin = request.url.encodedPath
                if (chemin == "/api/flux") return json(200, flux)
                return fixes[chemin]?.let { json(200, it) } ?: super.dispatch(request)
            }
        }
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
        val sessions = SessionsParHote(client)
        return ParcoursEntree(
            Decouverte(client, baseApi = base),
            sessions,
            Connexion(sessions, baseApi = base),
            InstallationsConnues(stockage),
            baseApi = base,
        ) { preuve }
    }

    /** Le thème de l'appareil, gardé en mémoire le temps du test. */
    fun apparence() = Apparence(object : StockageApparence {
        private var valeur: String? = null
        override fun lire() = valeur
        override fun ecrire(valeur: String) { this.valeur = valeur }
    })

    fun residence(nom: String) = repondre(200, """{"site_nom": "$nom"}""")

    fun repondre(code: Int, corps: String = "{}") = serveur.enqueue(json(code, corps))

    private fun json(code: Int, corps: String) =
        MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(corps).build()

    override fun close() = serveur.close()

    companion object {
        const val FIL_VIDE = """{"delai_recent_jours": 7, "items": []}"""
    }
}
