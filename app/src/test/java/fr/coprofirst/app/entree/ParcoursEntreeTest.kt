// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.entree

import fr.coprofirst.app.session.Connexion
import fr.coprofirst.app.session.Decouverte
import fr.coprofirst.app.session.ErreurService
import fr.coprofirst.app.session.Installation
import fr.coprofirst.app.session.InstallationsConnues
import fr.coprofirst.app.session.PreuveHote
import fr.coprofirst.app.session.SessionsParHote
import fr.coprofirst.app.session.StockageResidences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

/** Le parcours d'entrée sur les choix de l'iOS : reconnaissance pendant la frappe. */
@OptIn(ExperimentalCoroutinesApi::class)
class ParcoursEntreeTest {
    private val serveur = MockWebServer()
    private val ordonnanceur = StandardTestDispatcher()
    private val stockage = object : StockageResidences {
        var valeur: String? = null
        override fun lire() = valeur
        override fun ecrire(valeur: String) { this.valeur = valeur }
    }
    private val connues = InstallationsConnues(stockage)
    private var preuve = PreuveHote.Declaree
    private lateinit var parcours: ParcoursEntree

    @Before
    fun preparer() {
        Dispatchers.setMain(ordonnanceur)
        serveur.start()
        // Toute adresse mène au serveur local : seule la base de l'API change.
        val base = { _: Any -> serveur.url("/api").toString() }
        val decouverte = Decouverte(OkHttpClient(), Dispatchers.Unconfined, base)
        val sessions = SessionsParHote(OkHttpClient())
        val connexion = Connexion(sessions, Dispatchers.Unconfined, base)
        parcours = ParcoursEntree(decouverte, sessions, connexion, connues, baseApi = base) { preuve }
    }

    @After
    fun ranger() {
        serveur.close()
        Dispatchers.resetMain()
    }

    private fun residence(nom: String) = serveur.enqueue(
        MockResponse.Builder().code(200).addHeader("Content-Type", "application/json")
            .body("""{"site_nom": "$nom"}""").build(),
    )

    private fun ok() = serveur.enqueue(
        MockResponse.Builder().code(200).addHeader("Content-Type", "application/json").body("{}").build(),
    )

    /** Reconnaît la résidence et remplit les identifiants. */
    private fun TestScope.pret(hote: String) {
        residence("Les Glycines")
        parcours.entrer()
        parcours.saisir(hote)
        advanceUntilIdle()
        parcours.saisirEmail("a@exemple.test")
        parcours.saisirMotDePasse("secret")
    }

    private fun test(corps: suspend TestScope.() -> Unit) = runTest(ordonnanceur) { corps() }

    @Test
    fun ceQuiNEstPasEncoreUneAdresseNeDitRien() = test {
        parcours.entrer()
        parcours.saisir("residence")
        advanceUntilIdle()
        assertEquals(0, serveur.requestCount)
        assertNull(parcours.etat.value.erreur)
    }

    @Test
    fun reconnueApresLaPauseDeFrappeUneSeuleFois() = test {
        residence("Les Glycines")
        parcours.entrer()
        parcours.saisir("residence.exemple.f")
        advanceTimeBy(300)
        parcours.saisir("residence.exemple.test")
        advanceTimeBy(ParcoursEntree.PAUSE_DE_FRAPPE_MS - 1)
        assertEquals(0, serveur.requestCount)
        advanceUntilIdle()
        assertEquals(1, serveur.requestCount)
        assertEquals("Les Glycines", parcours.etat.value.reconnue?.nom)
        // Comme l'iOS : une résidence n'est retenue qu'après une connexion réussie.
        assertEquals(emptyList<Installation>(), connues.liste)
    }

    @Test
    fun laDerniereResidencePreRemplitEtSeReconnait() = test {
        connues.retenir(Installation("https://5hostachy.exemple.test", "Ancienne"))
        residence("5Hostachy")
        parcours.entrer()
        advanceUntilIdle()
        assertEquals("5hostachy.exemple.test", parcours.etat.value.saisie)
        assertEquals("5Hostachy", parcours.etat.value.reconnue?.nom)
    }

    @Test
    fun laDemoOuvreLaConnexionASonAdresse() = test {
        residence("Résidence Les Glycines")
        parcours.entrerDemo()
        advanceUntilIdle()
        assertEquals(EcranEntree.Connexion, parcours.etat.value.ecran)
        assertEquals("demo.coprofirst.fr", parcours.etat.value.saisie)
        assertEquals("Résidence Les Glycines", parcours.etat.value.reconnue?.nom)
    }

    @Test
    fun unEchecSEffaceDesQueLaSaisieChange() = test {
        serveur.enqueue(MockResponse.Builder().code(421).build())
        parcours.entrer()
        parcours.saisir("inconnue.exemple.test")
        advanceUntilIdle()
        assertSame(ErreurService.HoteInconnu, parcours.etat.value.erreur)
        parcours.saisir("inconnue.exemple.f")
        assertNull(parcours.etat.value.erreur)
        parcours.retour()
        assertEquals(EcranEntree.Accueil, parcours.etat.value.ecran)
    }

    @Test
    fun unHoteJamaisVuSeConfirmeAvantToutMotDePasse() = test {
        pret("jamais-vu.exemple.test")
        parcours.seConnecter()
        advanceUntilIdle()
        // Seul GET /config est parti : le mot de passe attend la confirmation.
        assertEquals(1, serveur.requestCount)
        assertEquals(PreuveHote.Declaree, parcours.etat.value.confirmation?.preuve)
        ok()
        parcours.confirmerHote()
        advanceUntilIdle()
        serveur.takeRequest()
        assertEquals("/api/auth/login", serveur.takeRequest().url.encodedPath)
        assertEquals(EcranEntree.Espace, parcours.etat.value.ecran)
        assertEquals("", parcours.etat.value.motDePasse)
        assertEquals("https://jamais-vu.exemple.test", connues.liste.single().url)
    }

    @Test
    fun sansPreuveIlFautLeSecondAccord() = test {
        preuve = PreuveHote.Absente
        pret("imitation.exemple.test")
        parcours.seConnecter()
        advanceUntilIdle()
        parcours.confirmerHote()
        assertEquals(1, serveur.requestCount)
        parcours.basculerSecondAccord()
        ok()
        parcours.confirmerHote()
        advanceUntilIdle()
        assertEquals(2, serveur.requestCount)
        assertEquals(EcranEntree.Espace, parcours.etat.value.ecran)
    }

    @Test
    fun laDemoEtUneResidenceConnueNeSeConfirmentPas() = test {
        connues.retenir(Installation("https://connue.exemple.test", "Connue"))
        pret("connue.exemple.test")
        ok()
        parcours.seConnecter()
        advanceUntilIdle()
        assertNull(parcours.etat.value.confirmation)
        assertEquals(EcranEntree.Espace, parcours.etat.value.ecran)
    }

    @Test
    fun identifiantsRefusesSansQuitterLEcran() = test {
        connues.retenir(Installation("https://connue.exemple.test", "Connue"))
        pret("connue.exemple.test")
        serveur.enqueue(MockResponse.Builder().code(422).build())
        parcours.seConnecter()
        advanceUntilIdle()
        assertSame(ErreurService.IdentifiantsRefuses, parcours.etat.value.erreurConnexion)
        assertEquals(EcranEntree.Connexion, parcours.etat.value.ecran)
        parcours.saisirMotDePasse("autre")
        assertNull(parcours.etat.value.erreurConnexion)
    }

    @Test
    fun oublierToutRevientALAccueilCommeNeuve() = test {
        connues.retenir(Installation("https://connue.exemple.test", "Connue"))
        pret("connue.exemple.test")
        ok()
        parcours.seConnecter()
        advanceUntilIdle()
        assertEquals(EcranEntree.Espace, parcours.etat.value.ecran)
        ok() // POST /auth/logout
        parcours.oublierTout()
        advanceUntilIdle()
        assertEquals(EtatEntree(), parcours.etat.value)
        assertEquals(emptyList<Installation>(), connues.liste)
    }

    /** #57 : la session ouverte parle à la même API que la connexion (le serveur local ici). */
    @Test
    fun laSessionOuverteParleALaMemeApi() = test {
        connues.retenir(Installation("https://connue.exemple.test", "Connue"))
        pret("connue.exemple.test")
        ok()
        parcours.seConnecter()
        advanceUntilIdle()
        val avant = serveur.requestCount
        residence("Connue")
        val cles = parcours.sessionOuverte()!!.appeler { b, c -> fr.coprofirst.api.routes.ConfigApi(b, c).getConfig() }
        assertEquals("Connue", cles["site_nom"])
        assertEquals(avant + 1, serveur.requestCount)
    }
}
