// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.entree

import fr.coprofirst.app.session.Decouverte
import fr.coprofirst.app.session.ErreurEntree
import fr.coprofirst.app.session.InstallationsConnues
import fr.coprofirst.app.session.StockageResidences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ParcoursEntreeTest {
    private val serveur = MockWebServer()
    private val stockage = object : StockageResidences {
        var valeur: String? = null
        override fun lire() = valeur
        override fun ecrire(valeur: String) { this.valeur = valeur }
    }
    private lateinit var parcours: ParcoursEntree

    @Before
    fun preparer() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        serveur.start()
        val decouverte = Decouverte(OkHttpClient(), Dispatchers.Unconfined) { serveur.url("/api").toString() }
        parcours = ParcoursEntree(decouverte, InstallationsConnues(stockage))
    }

    @After
    fun ranger() {
        serveur.close()
        Dispatchers.resetMain()
    }

    @Test
    fun saisieQuiNEstPasUneAdresse() {
        parcours.ouvrirAdresse()
        parcours.saisir("pas une adresse")
        parcours.entrerSaisie()
        assertSame(ErreurEntree.AdresseInvalide, parcours.etat.value.erreur)
        assertEquals(EcranEntree.Adresse, parcours.etat.value.ecran)
        assertEquals(0, serveur.requestCount)
    }

    @Test
    fun residenceReconnueEtRetenue() {
        serveur.enqueue(
            MockResponse.Builder().code(200).addHeader("Content-Type", "application/json")
                .body("""{"site_nom": "Les Glycines"}""").build(),
        )
        parcours.ouvrirAdresse()
        parcours.saisir("Residence.Exemple.fr")
        parcours.entrerSaisie()
        val ecran = parcours.etat.value.ecran as EcranEntree.Residence
        assertEquals("Les Glycines", ecran.installation.nom)
        assertEquals("https://residence.exemple.fr", ecran.installation.url)
        assertEquals(listOf(ecran.installation), parcours.etat.value.connues)
        parcours.oublier(ecran.installation)
        assertEquals(EcranEntree.Accueil, parcours.etat.value.ecran)
        assertEquals(emptyList<Any>(), parcours.etat.value.connues)
    }
}
