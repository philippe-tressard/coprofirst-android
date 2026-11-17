// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.entree

import fr.coprofirst.app.commun.Apparence
import fr.coprofirst.app.commun.StockageApparence
import fr.coprofirst.app.session.ClientHttp
import fr.coprofirst.app.session.Connexion
import fr.coprofirst.app.session.Decouverte
import fr.coprofirst.app.session.Installation
import fr.coprofirst.app.session.InstallationsConnues
import fr.coprofirst.app.session.LecturesGardees
import fr.coprofirst.app.session.PreuveHote
import fr.coprofirst.app.session.SessionOuverte
import fr.coprofirst.app.session.SessionsGardees
import fr.coprofirst.app.session.SessionsParHote
import fr.coprofirst.app.session.StockageResidences
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.QueueDispatcher
import mockwebserver3.RecordedRequest
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Un serveur local EN HTTPS (le clair est refusé par le manifeste, comme en vrai) et le
 * parcours d'entrée qui lui parle — le client de l'app, qui fait confiance à son seul
 * certificat. Toute adresse saisie mène à lui.
 *
 * GET /flux (le fil, la première page d'une résidence ouverte) se sert HORS de la file, depuis
 * `flux` : il part en même temps que GET /auth/me, et prendrait sinon la réponse de l'autre.
 * GET /notifications aussi, depuis `notifications` (aucune par défaut) : la cloche relit ses
 * non lues à l'ouverture de la résidence (#120), en même temps que les deux autres.
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

    /** Ce que sert GET /notifications : aucune alerte, sauf si le test en pose. */
    @Volatile
    var notifications: String = "[]"

    /** Les routes servies hors de la file, par chemin (`/api/sondages/3`). */
    val fixes = ConcurrentHashMap<String, String>()

    /** Les requêtes qui attendent une réponse de la file (aucune n'y est encore posée). */
    private val enAttente = AtomicInteger()

    @Volatile
    private var fermant = false
    val serveur = MockWebServer().apply {
        dispatcher = object : QueueDispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val chemin = request.url.encodedPath
                if (chemin == "/api/flux") return json(200, flux)
                if (chemin == "/api/notifications" && request.method == "GET") return json(200, notifications)
                fixes[chemin]?.let { return json(200, it) }
                if (fermant) return json(503, "{}")
                enAttente.incrementAndGet()
                try {
                    return super.dispatch(request)
                } finally {
                    enAttente.decrementAndGet()
                }
            }
        }
        useHttps(serveurTls.sslSocketFactory())
        start()
    }
    internal val client = ClientHttp.partage.newBuilder()
        .sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager)
        .build()

    /**
     * Le parcours d'entrée, sur ce serveur. Par défaut, un appareil qui ne garde aucune
     * session (pas de « Garder la session ? ») et n'a ouvert aucune résidence ; `gardees` et
     * `connues` posent un passage précédent (#13).
     */
    fun parcours(
        preuve: PreuveHote = PreuveHote.Declaree,
        gardees: SessionsGardees = SessionsGardees.Indisponibles,
        connues: InstallationsConnues = residencesEnMemoire(),
        lectures: LecturesGardees? = null,
    ): ParcoursEntree {
        val base = { _: Any -> serveur.url("/api").toString() }
        val sessions = SessionsParHote(client, gardees, lectures)
        return ParcoursEntree(
            Decouverte(client, baseApi = base),
            sessions,
            Connexion(sessions, baseApi = base),
            connues,
            baseApi = base,
        ) { preuve }
    }

    /** Des résidences connues (et un coffre) gardées en mémoire, le temps du test. */
    fun residencesEnMemoire() = InstallationsConnues(object : StockageResidences {
        private var valeur: String? = null
        override fun lire() = valeur
        override fun ecrire(valeur: String) { this.valeur = valeur }
    })

    /** Une résidence déjà ouverte sur ce serveur, sans passer par l'entrée (un écran de la résidence seul). */
    fun session(installation: Installation): SessionOuverte =
        SessionOuverte(installation, SessionsParHote(client), baseApi = { serveur.url("/api").toString() })

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

    /**
     * Ferme le serveur SANS fil qui écrive encore (#251). Une requête sans réponse posée dans la
     * file (la fiche qu'un test n'a pas préparée, GET /auth/me) attend : `MockWebServer.close`
     * fermerait ses sockets, PUIS réveillerait ce fil, qui écrirait sur un socket TLS libéré —
     * `NullPointerException: ssl == null` de Conscrypt, sur un fil du serveur, et tout le
     * processus de test tombe. Chaque requête en attente reçoit donc sa réponse (503), les
     * suivantes aussi, et le client a fini de les lire avant que le serveur ne ferme.
     */
    override fun close() {
        fermant = true
        repeat(enAttente.get()) { repondre(503) }
        val limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while ((enAttente.get() > 0 || lectureEnCours()) && System.nanoTime() < limite) Thread.sleep(20)
        client.connectionPool.evictAll()
        serveur.close()
    }

    /** Une connexion du client porte encore un échange : le serveur peut y écrire. */
    private fun lectureEnCours() = client.connectionPool.let { it.connectionCount() > it.idleConnectionCount() }

    /** Les requêtes qui attendent encore leur réponse dans la file — 0 après `close`. */
    internal fun requetesEnAttente() = enAttente.get()

    companion object {
        const val FIL_VIDE = """{"delai_recent_jours": 7, "items": []}"""
    }
}
