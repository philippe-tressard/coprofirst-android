// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.session

import fr.coprofirst.api.routes.ConfigApi
import fr.coprofirst.api.routes.FluxApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Duration
import java.time.Instant
import javax.crypto.spec.SecretKeySpec

/** La consultation hors ligne (#28), comme `LectureGardeeTests` de l'iOS : le magasin et sa porte. */
class LecturesGardeesTest {
    @get:Rule
    val dossier = TemporaryFolder()
    private var maintenant = Instant.parse("2026-11-10T13:32:00Z")
    private val gardees = SessionsGardeesEnMemoire()
    private val chiffrement = ChiffrementAesGcm { SecretKeySpec(ByteArray(32) { it.toByte() }, "AES") }
    private val lectures by lazy { LecturesGardees(dossier.root, chiffrement, gardees::contient) { maintenant } }
    private val hote = "residence.exemple.test"

    private val serveur = MockWebServer().apply { start() }
    private val sessions by lazy { SessionsParHote(OkHttpClient(), gardees, lectures) }
    private val base = { _: AdresseInstallation -> serveur.url("/api").toString() }
    private val session by lazy {
        SessionOuverte(Installation("https://$hote", "Les Glycines"), sessions, Dispatchers.Unconfined, base)
    }

    private companion object {
        const val FIL = """{"delai_recent_jours": 7, "items": []}"""
    }

    @After
    fun arreter() = serveur.close()

    private fun repondre(code: Int, corps: String = "{}") = serveur.enqueue(
        MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(corps).build(),
    )

    private suspend fun fil() = session.appeler { b, c -> FluxApi(b, c).getFlux() }

    @Test
    fun rienNEstGardeSansSessionGardee() {
        lectures.garder("{}".toByteArray(), null, "/api/flux", hote)
        assertEquals(LectureDisponible.Aucune, lectures.consulter(hote))
        assertEquals(0, dossier.root.listFiles().orEmpty().size)
    }

    @Test
    fun uneLectureGardeeSeRelitChiffreeSurLeDisque() {
        gardees.garder(hote, "r1")
        lectures.garder("Fuite d'eau".toByteArray(), "application/json", "/api/flux", hote)
        val lue = requireNotNull(lectures.lire("/api/flux", hote))
        assertArrayEquals("Fuite d'eau".toByteArray(), lue.corps)
        assertEquals("application/json", lue.type)
        assertEquals(maintenant, lue.date)
        // Rien en clair : ni le corps, ni l'hôte, ni la route.
        val ecrit = dossier.root.walkTopDown().filter { it.isFile }.map { String(it.readBytes(), Charsets.ISO_8859_1) }.joinToString()
        assertFalse(ecrit.contains("Fuite"))
        assertFalse(dossier.root.walkTopDown().any { it.name.contains("residence") || it.name.contains("flux") })
    }

    @Test
    fun uneLectureTropAncienneSEfaceEtSeDit() {
        gardees.garder(hote, "r1")
        lectures.garder("{}".toByteArray(), null, "/api/flux", hote)
        maintenant += Duration.ofDays(LecturesGardees.JOURS_MAX + 1)
        assertEquals(LectureDisponible.Perimee, lectures.consulter(hote))
        assertNull(lectures.lire("/api/flux", hote))
        assertEquals(LectureDisponible.Aucune, lectures.consulter(hote))
    }

    @Test
    fun auLancementRienNeResteDUneSessionQuiNEstPlusGardee() {
        gardees.garder(hote, "r1")
        gardees.garder("autre.exemple.test", "r2")
        lectures.garder("{}".toByteArray(), null, "/api/flux", hote)
        lectures.garder("{}".toByteArray(), null, "/api/flux", "autre.exemple.test")
        lectures.purger(setOf(hote))
        assertNotNull(lectures.lire("/api/flux", hote))
        assertNull(lectures.lire("/api/flux", "autre.exemple.test"))
        lectures.purger()
        assertNull(lectures.lire("/api/flux", hote))
    }

    @Test
    fun enLigneUneLectureDeLaListeBlancheSeGardeEtPasLesAutres() = runTest {
        gardees.garder(hote, "r1")
        repondre(200, FIL)
        fil()
        repondre(200, """{"site_nom": "Les Glycines"}""")
        session.appeler { b, c -> ConfigApi(b, c).getConfig() }
        assertNotNull(lectures.lire("/api/flux", hote))
        assertNull(lectures.lire("/api/config", hote))
    }

    @Test
    fun enConsultationLaLectureGardeeEstServieEtRienNePart() = runTest {
        gardees.garder(hote, "r1")
        repondre(200, FIL)
        fil()
        assertTrue(lectures.consulter(hote) is LectureDisponible.Gardee)
        val avant = serveur.requestCount
        assertEquals(7, fil().delaiRecentJours)
        // Une lecture non gardée, ou une écriture : refusées ici, avant de partir.
        val refus = runCatching { session.appeler { b, c -> ConfigApi(b, c).getConfig() } }.exceptionOrNull()
        assertEquals(ErreurService.HorsLigne, refus)
        assertEquals(avant, serveur.requestCount)
    }

    @Test
    fun unElementQueLeCompteNePeutPlusOuvrirSeRetire() = runTest {
        gardees.garder(hote, "r1")
        repondre(200, FIL)
        fil()
        repondre(404, "{}")
        runCatching { fil() }
        assertNull(lectures.lire("/api/flux", hote))
    }

    @Test
    fun uneSessionRefuseeEfaceToutCeQuiEstGarde() = runTest {
        gardees.garder(hote, "r1")
        repondre(200, FIL)
        fil()
        repondre(401)
        repondre(401) // POST /auth/refresh refusé
        runCatching { fil() }
        assertEquals(LectureDisponible.Aucune, lectures.consulter(hote))
    }
}
