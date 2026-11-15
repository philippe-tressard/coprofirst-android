// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.entree

import fr.coprofirst.app.session.Decouverte
import fr.coprofirst.app.session.ErreurEntree
import fr.coprofirst.app.session.Installation
import fr.coprofirst.app.session.InstallationsConnues
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
    private lateinit var parcours: ParcoursEntree

    @Before
    fun preparer() {
        Dispatchers.setMain(ordonnanceur)
        serveur.start()
        // Toute adresse mène au serveur local : seule la base de l'API change.
        val decouverte = Decouverte(OkHttpClient(), Dispatchers.Unconfined) { serveur.url("/api").toString() }
        parcours = ParcoursEntree(decouverte, connues)
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
        parcours.saisir("residence.exemple.fr")
        advanceTimeBy(ParcoursEntree.PAUSE_DE_FRAPPE_MS - 1)
        assertEquals(0, serveur.requestCount)
        advanceUntilIdle()
        assertEquals(1, serveur.requestCount)
        assertEquals("Les Glycines", parcours.etat.value.reconnue?.nom)
        assertEquals("https://residence.exemple.fr", connues.liste.single().url)
    }

    @Test
    fun laDerniereResidencePreRemplitEtSeReconnait() = test {
        connues.retenir(Installation("https://5hostachy.exemple.fr", "Ancienne"))
        residence("5Hostachy")
        parcours.entrer()
        advanceUntilIdle()
        assertEquals("5hostachy.exemple.fr", parcours.etat.value.saisie)
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
        parcours.saisir("inconnue.exemple.fr")
        advanceUntilIdle()
        assertSame(ErreurEntree.HoteInconnu, parcours.etat.value.erreur)
        parcours.saisir("inconnue.exemple.f")
        assertNull(parcours.etat.value.erreur)
        parcours.retour()
        assertEquals(EcranEntree.Accueil, parcours.etat.value.ecran)
    }
}
