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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
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
import java.time.Instant

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
                    simules.repondre(request.method, chemin, request.body?.utf8().orEmpty())
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
        assertEquals(listOf(1, 2, 3, 4, 5), donnees.prestataires.map { it.id })
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
        assertEquals(5, donnees.prestataires.size)
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
    fun leRemplacementEstLuTelQuilEstServi() = runTest {
        val contrats = service.contrats(archives = false).associateBy { it.id }
        val ancien = contrats.getValue(2).remplacement
        assertEquals("2026-11-30", ancien.resilieLe)
        assertEquals(ContratLie(4, "Vérification des extincteurs", "Protection Incendie Exemple", "2026-12-01"), ancien.remplacePar)
        assertTrue(ancien.estDatee)
        val nouveau = contrats.getValue(4).remplacement
        assertEquals(listOf("Contrôles Sécurité Exemple"), nouveau.remplace.map { it.prestataireNom })
        assertEquals("sans remplacement : rien", RemplacementContrat(), contrats.getValue(1).remplacement)
        assertEquals(3, service.prestataires(archives = false).first().nbNotations)
    }

    @Test
    fun leRecapitulatifDesAvisEstCeluiQueLApiSert() = runTest {
        val exercices = service.interventions(1)
        assertTrue(simules.recues.contains("GET /api/prestataires/1/interventions"))
        assertEquals(listOf(2026, 2025), exercices.map { it.annee })
        val recent = exercices.first()
        assertEquals(2 to 1, recent.nbInterventions to recent.nbNotees)
        assertEquals("le compte servi, sans les états à zéro", listOf("excellent" to 2), recent.comptes)
        assertEquals(listOf("neutre" to 1), exercices[1].comptes)
        assertEquals("les interventions, puis les avis sans affaire", listOf("t45", "t38", "generaux"), recent.lignes.map { it.cle })
        assertTrue("une intervention non notée", recent.lignes[1].avis.isEmpty())
        val avis = recent.lignes.first().avis.single()
        assertEquals("excellent", avis.etat)
        assertEquals("Conseil syndical", avis.auteurNom)
        assertEquals("le droit servi, jamais recalculé", true, recent.lignes.last().avis.single().peutModifier)
        assertEquals(emptyList<ExerciceAvis>(), service.interventions(2))
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


    @Test
    fun leDepotRelitApresUnGesteSurUnAvis() = runTest {
        val depot = DepotPrestataires(service)
        depot.donnees(0)
        depot.oublier()
        depot.donnees(0)
        assertEquals("le nombre d'avis est servi avec la liste", 2, simules.recues.count { it == "GET /api/prestataires" })
    }

    @Test
    fun noterEnvoieLAvisEtSaConfirmation() = runTest {
        val saisie = SaisieAvis(etat = "mauvais", libre = "  Retard de trois semaines ", affaire = 52, confirmeAjout = true)
        service.noter(saisie.creation(prestataire = 1, contrat = null, ajoutRequis = true))
        val envoye = json(simules.corps.getValue("POST /prestataires/notations"))
        assertEquals("1", envoye.getValue("prestataire_id").toString())
        assertEquals("\"mauvais\"", envoye.getValue("etat").toString())
        assertEquals("\"Retard de trois semaines\"", envoye.getValue("commentaire").toString())
        assertEquals("52", envoye.getValue("ticket_id").toString())
        assertEquals("true", envoye.getValue("confirmer_ajout").toString())
        assertFalse("hors contrat, aucun contrat_id", envoye.containsKey("contrat_id"))
    }

    @Test
    fun remettreSansAffaireEnvoieUnNullExplicite() = runTest {
        val avant = AvisPrestataire(12, "excellent", "Astreinte joignable de nuit.", creeLe = Instant.EPOCH, peutModifier = true, ticketId = 45)
        val saisie = SaisieAvis.corrigeant(avant).copy(affaire = null)
        service.corriger(12, saisie.correction(avant, ajoutRequis = false))
        val envoye = json(simules.corps.getValue("PATCH /prestataires/notations/12"))
        assertEquals("ticket_id: null EXPLICITE (#99, model_fields_set)", JsonNull, envoye["ticket_id"])
        assertFalse("rien d'autre n'a changé : rien d'autre ne part", envoye.containsKey("etat") || envoye.containsKey("commentaire"))
    }

    @Test
    fun retirerEtLesAffairesParLeursRoutes() = runTest {
        service.retirer(12)
        assertEquals(listOf(45, 38, 52), service.affairesANoter(1).map { it.id })
        assertEquals(listOf(52), service.rechercherAffaires("toiture"))
        assertTrue(
            simules.recues.containsAll(
                listOf(
                    "DELETE /api/prestataires/notations/12",
                    "GET /api/prestataires/affaires-a-noter?prestataire_id=1",
                    "GET /api/tickets/recherche?q=toiture",
                ),
            ),
        )
    }

    private fun json(texte: String) = Json.parseToJsonElement(texte).jsonObject

    @Test(expected = ErreurService::class)
    fun uneFicheIllisibleEstUneErreur() = runTest {
        serveur.close()
        DonneesPrestataires.lire(service)
    }
}
