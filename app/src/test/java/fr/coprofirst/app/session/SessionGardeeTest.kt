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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** La session gardée sur l'appareil (#13) : l'activer, la rouvrir, la perdre, l'oublier. */
class SessionGardeeTest {
    private val serveur = MockWebServer().apply { start() }
    private val gardees = SessionsGardeesEnMemoire(ProtectionSession.CodeAppareil)
    private val sessions = SessionsParHote(OkHttpClient(), gardees)
    private val connexion = Connexion(sessions, Dispatchers.Unconfined) { serveur.url("/api").toString() }
    private val residence = Installation("https://residence.exemple.test", "Les Glycines")
    private val hote = "residence.exemple.test"
    private val deverrouillage = Deverrouillage { chiffre, _ -> chiffre }

    @After
    fun arreter() = serveur.close()

    private fun repondre(code: Int, vararg entetes: Pair<String, String>) {
        val r = MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body("{}")
        entetes.forEach { (n, v) -> r.addHeader(n, v) }
        serveur.enqueue(r.build())
    }

    private suspend fun reprise(): Throwable? = runCatching { connexion.reprendre(residence, deverrouillage) }.exceptionOrNull()

    @Test
    fun activerGardeLeJetonDeRafraichissementEtDitSaProtection() = runTest {
        repondre(200, "Set-Cookie" to "access_token=a; Path=/; HttpOnly", "Set-Cookie" to "refresh_token=r1; Path=/; HttpOnly")
        connexion.connecter(residence, "a@exemple.test", "secret")
        assertEquals(ProtectionSession.CodeAppareil, connexion.garder(residence))
        assertTrue(connexion.aUneSessionGardee(residence))
        assertEquals(LectureSession.Jeton("r1"), gardees.lire(hote, deverrouillage))
    }

    @Test
    fun sansJetonRienNeSeGarde() = runTest {
        assertNull(connexion.garder(residence))
        assertFalse(connexion.aUneSessionGardee(residence))
    }

    @Test
    fun laRepriseRenouvelleAvecLeJetonGardeEtGardeLeNeuf() = runTest {
        gardees.garder(hote, "r1")
        repondre(200, "Set-Cookie" to "refresh_token=r2; Path=/; HttpOnly")
        assertNull(reprise())
        val requete = serveur.takeRequest()
        assertEquals("/api/auth/refresh", requete.url.encodedPath)
        assertEquals("refresh_token=r1", requete.headers["Cookie"])
        assertEquals(LectureSession.Jeton("r2"), gardees.lire(hote, deverrouillage))
    }

    @Test
    fun unJetonRefuseEfaceLaSessionGardee() = runTest {
        gardees.garder(hote, "r1")
        repondre(401)
        assertEquals(ReprisePerdue, reprise())
        assertFalse(connexion.aUneSessionGardee(residence))
    }

    @Test
    fun uneAnnulationGardeLaSessionEtNeParlePasAuServeur() = runTest {
        gardees.garder(hote, "r1")
        gardees.refuser = true
        assertEquals(RepriseAnnulee, reprise())
        assertTrue(connexion.aUneSessionGardee(residence))
        assertEquals(0, serveur.requestCount)
    }

    @Test
    fun sansReseauLaSessionResteGardee() = runTest {
        gardees.garder(hote, "r1")
        serveur.close()
        assertEquals(ErreurService.Injoignable, reprise())
        assertTrue(connexion.aUneSessionGardee(residence))
    }

    @Test
    fun laDeconnexionEfaceLaSessionGardee() = runTest {
        gardees.garder(hote, "r1")
        repondre(200)
        connexion.deconnecter(residence)
        assertFalse(connexion.aUneSessionGardee(residence))
    }

    @Test
    fun leCourrielSeRetientAuCoffreEtSOublieAvecLesResidences() {
        val coffre = CoffreEnMemoire()
        val connues = InstallationsConnues(object : StockageResidences {
            var valeur: String? = null
            override fun lire() = valeur
            override fun ecrire(valeur: String) { this.valeur = valeur }
        }, coffre)
        connues.retenir(residence)
        connues.retenirIdentifiant(" a@exemple.test ", residence)
        assertEquals("a@exemple.test", connues.identifiant(residence))
        assertFalse(connues.gardeProposee(residence))
        connues.noterGardeProposee(residence)
        assertTrue(connues.gardeProposee(residence))
        connues.vider()
        assertNull(connues.identifiant(residence))
        assertFalse(connues.gardeProposee(residence))
    }
}
