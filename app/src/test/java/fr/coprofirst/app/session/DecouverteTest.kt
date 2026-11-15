// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.session

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

/** La découverte contre un serveur local : ce que l'écran dira pour chaque réponse. */
class DecouverteTest {
    private val serveur = MockWebServer()
    private val adresse = AdresseInstallation.normaliser("residence.exemple.fr")!!
    private lateinit var decouverte: Decouverte

    @Before
    fun demarrer() {
        serveur.start()
        // Le client de l'app, pour juger SES réglages (redirections) ; seule la base change.
        decouverte = Decouverte(ClientHttp.partage, Dispatchers.Unconfined) { serveur.url("/api").toString() }
    }

    @After
    fun arreter() = serveur.close()

    private fun repondre(code: Int, corps: String = "", entetes: Map<String, String> = emptyMap()) {
        val reponse = MockResponse.Builder().code(code).body(corps)
        entetes.forEach { (nom, valeur) -> reponse.addHeader(nom, valeur) }
        serveur.enqueue(reponse.build())
    }

    private suspend fun echec(): ErreurEntree = try {
        decouverte.decouvrir(adresse)
        error("découverte acceptée")
    } catch (erreur: ErreurEntree) {
        erreur
    }

    @Test
    fun residenceReconnue() = runTest {
        repondre(200, """{"site_nom": "Les Glycines", "version_contrat": "99.0.0"}""", mapOf("Content-Type" to "application/json"))
        val installation = decouverte.decouvrir(adresse)
        assertEquals("Les Glycines", installation.nom)
        assertEquals("/api/config", serveur.takeRequest().url.encodedPath)
    }

    @Test
    fun aucuneResidenceInstallee() = runTest {
        repondre(421)
        assertSame(ErreurEntree.HoteInconnu, echec())
    }

    @Test
    fun pageQuiNEstPasLApi() = runTest {
        repondre(200, "<html>Bienvenue</html>", mapOf("Content-Type" to "application/json"))
        assertSame(ErreurEntree.PasCoproFirst, echec())
    }

    @Test
    fun redirectionNonSuivie() = runTest {
        repondre(302, entetes = mapOf("Location" to "https://ailleurs.exemple.org/api/config"))
        assertSame(ErreurEntree.PasCoproFirst, echec())
        assertEquals(1, serveur.requestCount)
    }

    @Test
    fun erreurDuServeur() = runTest {
        repondre(500)
        assertSame(ErreurEntree.Inattendue, echec())
    }

    @Test
    fun adresseQuiNeRepondPas() = runTest {
        val base = serveur.url("/api").toString()
        serveur.close()
        val injoignable = Decouverte(OkHttpClient(), Dispatchers.Unconfined) { base }
        val erreur = try {
            injoignable.decouvrir(adresse)
            error("découverte acceptée")
        } catch (e: ErreurEntree) {
            e
        }
        assertSame(ErreurEntree.Injoignable, erreur)
    }
}
