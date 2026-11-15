// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.perimetres

import fr.coprofirst.app.session.AdresseInstallation
import fr.coprofirst.app.session.Installation
import fr.coprofirst.app.session.SessionOuverte
import fr.coprofirst.app.session.SessionsParHote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La résidence simulée qui sert l'arbre (`FixturesPerimetres`) et le nom d'une sélection,
 * et le service de l'app qui lui parle par son vrai client — le banc des tests du choix.
 */
class BancPerimetres : AutoCloseable {
    /** Les requêtes reçues : « MÉTHODE /chemin corps ». */
    val recues = mutableListOf<String>()
    private val serveur = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val corps = request.body?.utf8().orEmpty()
                synchronized(recues) { recues += "${request.method} ${request.url.encodedPath} $corps".trim() }
                val (code, reponse) = when ("${request.method} ${request.url.encodedPath}") {
                    "GET /api/perimetres" -> 200 to FixturesPerimetres.ARBRE
                    "POST /api/perimetres/libelle" -> 200 to FixturesPerimetres.libelle(
                        if (corps.contains("\"codes\":[\"${FixturesPerimetres.CODE_PAR_DEFAUT}\"]")) {
                            listOf(FixturesPerimetres.CODE_PAR_DEFAUT)
                        } else {
                            emptyList()
                        },
                    )
                    else -> 404 to """{"detail": "Not Found"}"""
                }
                return MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(reponse).build()
            }
        }
        start()
    }
    val service: ServicePerimetres = ServicePerimetresApi(
        SessionOuverte(
            Installation("https://residence.test", "Les Glycines"),
            SessionsParHote(OkHttpClient()),
            Dispatchers.Unconfined,
        ) { _: AdresseInstallation -> serveur.url("/api").toString() },
    )

    /** L'arbre tel que l'app le lit. */
    fun arbre() = runBlocking { service.perimetres() }

    override fun close() = serveur.close()
}

/** La lecture de l'arbre et le nom d'une sélection, par le client GÉNÉRÉ (GET /perimetres, POST /perimetres/libelle). */
class ServicePerimetresTest {
    private val banc = BancPerimetres()

    @After
    fun arreter() = banc.close()

    @Test
    fun lArbreSeLitActifsSeulementDansLOrdreServi() = runTest {
        val lus = banc.service.perimetres()
        assertEquals("GET /api/perimetres", banc.recues.single())
        assertEquals(
            listOf(
                "copropriete", "batiments", "bat_a", "bat_a/hall", "bat_a/toit", "bat_a_terrasses",
                "bat_a_terrasse_nord", "bat_b", "parking", "jardin",
            ),
            lus.map { it.code },
        )
        assertFalse("la loge inactive est écartée", lus.any { it.code == "bat_a/loge" })
    }

    @Test
    fun chaqueChampServiEstLu() = runTest {
        val lus = banc.service.perimetres().associateBy { it.code }
        val batA = lus.getValue("bat_a")
        assertEquals(Perimetre("bat_a", "Bâtiment A", 1, true, false, "batiments", "Bât. A", "", 1), batA)
        assertTrue(lus.getValue("copropriete").parDefaut)
        assertFalse(lus.getValue("batiments").selectionnable)
        assertNull(lus.getValue("parking").parent)
        assertEquals("copropriete", Perimetre.codeParDefaut(lus.values.toList()))
        assertNull(Perimetre.codeParDefaut(emptyList()))
    }

    @Test
    fun leLieuDUneSelectionEstNommeParLeServeur() = runTest {
        assertEquals("Bâtiment A › Toit", banc.service.lieu(listOf("bat_a/toit")))
        assertEquals("POST /api/perimetres/libelle {\"codes\":[\"bat_a/toit\"]}", banc.recues.single())
        assertEquals("Toute la copropriété", banc.service.lieu(listOf("copropriete")))
    }
}
