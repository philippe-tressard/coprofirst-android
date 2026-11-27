// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.entree

import fr.coprofirst.app.session.Connexion
import fr.coprofirst.app.session.Decouverte
import fr.coprofirst.app.session.Installation
import fr.coprofirst.app.session.InstallationsConnues
import fr.coprofirst.app.session.PreuveHote
import fr.coprofirst.app.session.SessionsParHote
import fr.coprofirst.app.session.StockageResidences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.QueueDispatcher
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before

/**
 * Le banc des tests du parcours d'entrée : un serveur local, le parcours qui lui parle, les
 * résidences connues en mémoire. Le serveur répond dans l'ordre de sa file, sauf GET /auth/me,
 * lu sitôt connecté (#391) : il sert [compte], ou 404 — le compte ne se lit pas, la résidence
 * s'ouvre quand même.
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class BancEntree {
    protected val serveur = MockWebServer().apply {
        dispatcher = object : QueueDispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.url.encodedPath == "/api/auth/me") json(compte?.let { 200 } ?: 404, compte ?: "{}") else super.dispatch(request)
        }
    }
    protected val ordonnanceur = StandardTestDispatcher()
    private val stockage = object : StockageResidences {
        var valeur: String? = null
        override fun lire() = valeur
        override fun ecrire(valeur: String) { this.valeur = valeur }
    }
    protected val connues = InstallationsConnues(stockage)
    protected var preuve = PreuveHote.Declaree

    /** Ce que sert GET /auth/me ; `null` : 404. */
    @Volatile
    protected var compte: String? = null
    protected lateinit var parcours: ParcoursEntree

    @Before
    fun preparer() {
        Dispatchers.setMain(ordonnanceur)
        serveur.start()
        construire()
    }

    protected fun construire(avantSortie: suspend () -> Unit = {}, apresSortie: (Installation) -> Unit = {}) {
        // Toute adresse mène au serveur local : seule la base de l'API change.
        val base = { _: Any -> serveur.url("/api").toString() }
        val decouverte = Decouverte(OkHttpClient(), Dispatchers.Unconfined, base)
        val sessions = SessionsParHote(OkHttpClient())
        val connexion = Connexion(sessions, Dispatchers.Unconfined, base)
        parcours = ParcoursEntree(decouverte, sessions, connexion, connues, baseApi = base, avantSortie = avantSortie, apresSortie = apresSortie) { preuve }
    }

    @After
    fun ranger() {
        serveur.close()
        Dispatchers.resetMain()
    }

    protected fun residence(nom: String) = serveur.enqueue(json(200, """{"site_nom": "$nom"}"""))

    protected fun ok() = serveur.enqueue(json(200, "{}"))

    private fun json(code: Int, corps: String) =
        MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(corps).build()

    /** Reconnaît la résidence et remplit les identifiants. */
    protected fun TestScope.pret(hote: String) {
        residence("Les Glycines")
        parcours.entrer()
        parcours.saisir(hote)
        advanceUntilIdle()
        parcours.saisirEmail("a@exemple.test")
        parcours.saisirMotDePasse("secret")
    }

    protected fun test(corps: suspend TestScope.() -> Unit) = runTest(ordonnanceur) { corps() }
}
