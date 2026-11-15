// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.espacecs

import fr.coprofirst.app.commun.TonAvis
import fr.coprofirst.app.fonctions.monlot.TypeAcces
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
import org.junit.Assert.fail
import org.junit.Test

/**
 * Les services réels de l'Espace CS, par le client GÉNÉRÉ, contre la résidence simulée :
 * chaque réponse de `FixturesEspaceCS` se lit comme une vraie, chaque geste part sur la
 * bonne route avec le bon corps, et l'état de la file suit le serveur.
 */
class ServiceEspaceCSTest {
    private val residence = ResidenceSimuleeCS()
    private val serveur = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val (code, corps) = residence.repondre(request.method, request.url.encodedPath, request.body?.utf8().orEmpty())
                return MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(corps).build()
            }
        }
        start()
    }
    private val session = SessionOuverte(
        Installation("https://residence.test", "Les Glycines"),
        SessionsParHote(OkHttpClient()),
        Dispatchers.Unconfined,
    ) { _: AdresseInstallation -> serveur.url("/api").toString() }
    private val validations = ServiceValidationsApi(session)
    private val badges = ServiceBadgesApi(session)

    @After
    fun arreter() = serveur.close()

    @Test
    fun laFileSeLitParLesTypesGeneres() = runTest {
        val file = validations.file()
        assertEquals(listOf(501, 502, 503), file.comptes.map { it.id })
        assertEquals("Camille EXEMPLE", file.comptes[0].nomAffiche)
        assertEquals("Bâtiment A", file.comptes[0].batimentNom)
        assertNull(file.comptes[0].nomAideAffiche)
        assertEquals("Claude FICTIF", file.comptes[2].nomAideAffiche)
        assertEquals(listOf(71, 72), file.commandes.map { it.id })
        assertEquals("Lot 104 — parking", file.commandes[1].lot)
        assertNull(file.commandes[1].batiment)
    }

    /** L'annonce du serveur passe telle quelle, avec son ton : l'avertissement reste un avertissement. */
    @Test
    fun validerRendLAnnonceDuServeur() = runTest {
        val etat = EtatValidations(validations, validations.file())
        val annonce = etat.valider(etat.comptes.first { it.id == 502 }, null, "accueil envoyé")
        assertEquals(TonAvis.Avertissement, annonce?.ton)
        assertEquals("Compte activé. ⚠️ Aucun lot trouvé dans l'import.", annonce?.texte)
        assertEquals(listOf(501, 503), etat.comptes.map { it.id })
        assertNull(etat.enCours)
        assertTrue(residence.recues.any { it.startsWith("POST /api/admin/comptes/502/traiter") && it.contains("\"action\":\"valider\"") })
    }

    /** « Nouvel Arrivant » : l'accueil part après la validation, et l'avis le dit à la suite de l'annonce. */
    @Test
    fun unNouvelArrivantEstAccueilli() = runTest {
        val etat = EtatValidations(validations, validations.file())
        val annonce = etat.valider(etat.comptes.first(), AccueilArrivant(" Bât. A ", ""), "Actions d'accueil envoyées.")
        assertEquals("Compte activé — 1 lot rattaché. Actions d'accueil envoyées.", annonce?.texte)
        assertEquals(TonAvis.Succes, annonce?.ton)
        val accueil = residence.recues.single { it.startsWith("POST /api/admin/utilisateurs/501/accueil-arrivant") }
        assertTrue(accueil, accueil.contains("\"batiment\":\"Bât. A\""))
        assertTrue("un champ vide ne part pas", !accueil.contains("ancien_resident\":\""))
    }

    @Test
    fun rejeterEtTraiterRetirentDeLaFile() = runTest {
        val etat = EtatValidations(validations, validations.file())
        etat.rejeter(etat.comptes.first { it.id == 503 })
        assertTrue(residence.recues.any { it.startsWith("POST /api/admin/comptes/503/traiter") && it.contains("\"refuser\"") })
        etat.traiter(etat.commandes.first { it.id == 71 }, codesSaisis("4521, 417D5927"))
        assertTrue(residence.recues.any { it.contains("/admin/commandes-acces/71/traiter") && it.contains("\"codes\":[\"4521\",\"417D5927\"]") && it.contains("\"accepter\"") })
        etat.traiter(etat.commandes.first { it.id == 72 }, null)
        assertTrue(residence.recues.any { it.contains("/admin/commandes-acces/72/traiter") && it.contains("\"refuser\"") })
        assertEquals(listOf(501, 502), etat.comptes.map { it.id })
        assertTrue(etat.commandes.isEmpty())
    }

    /** Un refus du serveur garde la file et rend sa phrase. */
    @Test
    fun unRefusGardeLaFile() = runTest {
        val etat = EtatValidations(validations, validations.file())
        val compte = etat.comptes.first()
        validations.traiterCompte(compte.id, valider = false)
        try {
            etat.rejeter(compte)
            fail("le serveur refuse un compte déjà traité")
        } catch (e: ErreurService.Refus) {
            assertEquals("Compte déjà traité", e.phraseServie)
        }
        assertEquals(3, etat.comptes.size)
        assertNull(etat.enCours)
    }

    @Test
    fun lesCodesSeDecoupentCommeLeSite() {
        assertEquals(listOf("4521", "417D5927", "TC-1"), codesSaisis(" 4521,417D5927 ; TC-1 "))
        assertEquals(emptyList<String>(), codesSaisis("  "))
    }

    @Test
    fun leParcSeLitEtSeRangeParPorteur() = runTest {
        val parc = badges.parc()
        assertEquals(5, parc.size)
        // Le rang servi (nom puis prénom), Vigik et télécommandes mêlés ; sans porteur, en dernier.
        assertEquals(listOf("vigik-2", "vigik-1", "telecommande-3", "vigik-4", "telecommande-5"), parc.filtrer(null, "").map { it.id })
        assertEquals(listOf("telecommande-3", "telecommande-5"), parc.filtrer(TypeAcces.Telecommande, "").map { it.id })
        // La recherche : tous les mots, sans accents ni casse, sur ce que la ligne affiche.
        assertEquals(listOf("vigik-4"), parc.filtrer(null, "celeste").map { it.id })
        assertEquals(listOf("vigik-1", "telecommande-3"), parc.filtrer(null, "alix lot").map { it.id })
        val sansLieu = parc.first { it.id == "vigik-4" }
        assertNull(sansLieu.lieu)
        assertEquals("Bât. A", parc.first { it.id == "vigik-1" }.lieu)
    }

    @Test
    fun unParcRefuseDitLaPhraseDuServeur() = runTest {
        residence.parcEnEchec = 403
        try {
            badges.parc()
            fail("le serveur refuse")
        } catch (e: ErreurService.Refus) {
            assertEquals("Accès réservé au conseil syndical", e.phraseServie)
        }
    }
}
