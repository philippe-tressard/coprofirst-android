// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.prestataires

import fr.coprofirst.app.session.AdresseInstallation
import fr.coprofirst.app.session.ErreurService
import fr.coprofirst.app.session.Installation
import fr.coprofirst.app.session.SessionOuverte
import fr.coprofirst.app.session.SessionsParHote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Chaque lecture part par SA route du contrat, par le client généré, et les réponses du
 * serveur (`FixturesPrestataires`) se lisent par le MÊME branchement que l'app.
 */
class ServicePrestatairesTest {
    private val simules = PrestatairesSimules()
    private var equipementsEnPanne = false
    private val serveur = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val chemin = request.url.encodedPath + (request.url.encodedQuery?.let { "?$it" } ?: "")
                val (code, corps) = if (equipementsEnPanne && chemin.endsWith("/equipements")) {
                    500 to "{}"
                } else {
                    simules.repondre(request.method, chemin)
                }
                return MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(corps).build()
            }
        }
        start()
    }
    private val base = { _: AdresseInstallation -> serveur.url("/api").toString() }
    private val service = ServicePrestatairesApi(
        SessionOuverte(Installation("https://residence.exemple.test", "Les Glycines"), SessionsParHote(OkHttpClient()), Dispatchers.Unconfined, base),
    )

    @After
    fun arreter() = serveur.close()

    @Test
    fun lesDonneesPartageesSeLisentDuContrat() = runTest {
        val donnees = DonneesPrestataires.lire(service)
        assertEquals(listOf(1, 2, 3, 4), donnees.prestataires.map { it.id })
        val ascenseurs = donnees.prestataires.first()
        assertEquals("maintenance_depannage", ascenseurs.type)
        assertEquals("les numéros SERVIS, pas le champ brut", listOf("01 99 00 00 11", "01 99 00 00 12"), ascenseurs.telephones)
        assertNull("un contact au nom vide n'a pas de nom", ascenseurs.contacts.single().nomAffiche)
        val contrat = donnees.contrats.first()
        assertEquals("2024-01-01", contrat.dateDebut)
        assertEquals("2026-11-18", contrat.prochaineVisite)
        assertEquals("↺ Mensuel", contrat.frequenceLibelle)
        assertEquals("Sécurité incendie", donnees.equipement("incendie"))
        assertEquals("un équipement inconnu reste brut", "autre", donnees.equipement("autre"))
        assertTrue(simules.recues.containsAll(listOf("GET /api/prestataires", "GET /api/prestataires/contrats", "GET /api/equipements")))
    }

    @Test
    fun sansEquipementsLEcranResteLisible() = runTest {
        equipementsEnPanne = true
        val donnees = DonneesPrestataires.lire(service)
        assertEquals(4, donnees.prestataires.size)
        assertEquals("ascenseur", donnees.equipement("ascenseur"))
    }

    @Test
    fun chaqueLectureParSaRoute() = runTest {
        assertEquals(listOf(9), service.prestataires(archives = true).map { it.id })
        assertEquals(listOf(8), service.contrats(archives = true).map { it.id })
        assertEquals(listOf(DocumentContrat(41, "Contrat signé", "contrat-ascenseurs.pdf", 245_000L)), service.documents(1))
        assertEquals(listOf("eau_chaude", "eau_froide"), service.compteurs().map { it.typeCompteur })
        val releves = service.releves("eau_froide")
        assertEquals(Releve(11, "2026-10-01", 47_047, "Relevé du syndic"), releves.first())
        assertTrue(
            simules.recues.containsAll(
                listOf(
                    "GET /api/prestataires/archives",
                    "GET /api/prestataires/contrats/archives",
                    "GET /api/documents?contrat_id=1",
                    "GET /api/prestataires/compteurs-config",
                    "GET /api/prestataires/releves?type_compteur=eau_froide",
                ),
            ),
        )
    }

    @Test
    fun leDepotNeRelitQuApresUneActualisation() = runTest {
        val depot = DepotPrestataires(service)
        depot.donnees(0)
        depot.donnees(0)
        assertEquals("une lecture pour deux onglets", 1, simules.recues.count { it == "GET /api/prestataires" })
        depot.donnees(1)
        assertEquals("tirer pour actualiser relit", 2, simules.recues.count { it == "GET /api/prestataires" })
    }

    @Test(expected = ErreurService::class)
    fun uneFicheIllisibleEstUneErreur() = runTest {
        serveur.close()
        DonneesPrestataires.lire(service)
    }
}
