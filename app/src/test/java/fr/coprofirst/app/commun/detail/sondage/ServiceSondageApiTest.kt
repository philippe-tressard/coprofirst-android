// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.detail.sondage

import fr.coprofirst.app.commun.CibleSignalement
import fr.coprofirst.app.commun.ServiceSignalementsApi
import fr.coprofirst.app.session.AdresseInstallation
import fr.coprofirst.app.session.ErreurService
import fr.coprofirst.app.session.Installation
import fr.coprofirst.app.session.SessionOuverte
import fr.coprofirst.app.session.SessionsParHote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
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
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Chaque geste de la fiche d'un sondage part par SA route du contrat, par le client généré
 * (GET /sondages/{id} et /auth/me, POST …/voter, …/commenter, DELETE …/commentaires/{cid},
 * POST /signalements), avec le corps que le site envoie. Tout est inventé.
 */
class ServiceSondageApiTest {
    /** Les requêtes reçues : « MÉTHODE /chemin corps ». */
    private val recues = CopyOnWriteArrayList<String>()

    /** La réponse de chaque route, par « MÉTHODE /chemin » : les deux lectures partent ensemble. */
    private val routes = mutableMapOf<String, Pair<Int, String>>()

    private val serveur = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val cle = "${request.method} ${request.url.encodedPath}"
                recues += "$cle ${request.body?.utf8().orEmpty()}".trim()
                val (code, corps) = routes[cle] ?: (404 to """{"detail": "Not Found"}""")
                return MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(corps).build()
            }
        }
        start()
    }
    private val base = { _: AdresseInstallation -> serveur.url("/api").toString() }
    private val session = SessionOuverte(
        Installation("https://residence.example.test", "Les Glycines"),
        SessionsParHote(OkHttpClient()),
        Dispatchers.Unconfined,
        base,
    )
    private val service = ServiceSondageApi(session)

    @After
    fun arreter() = serveur.close()

    private fun corps(texte: String): JsonElement = Json.parseToJsonElement(texte)

    @Test
    fun laFicheSeLitAvecLIdentifiantDuCompte() = runTest {
        routes["GET /api/sondages/12"] = 200 to FICHE
        routes["GET /api/auth/me"] = 200 to MOI
        val sondage = service.sondage(12)
        assertEquals(7, sondage.moi)
        assertEquals("Quel jour pour la fête des voisins ?", sondage.question)
        assertEquals(listOf(1, 4), sondage.options.map { it.id })
        assertEquals(16, sondage.totalVotes)
        assertTrue(sondage.peutVoter)
        assertEquals(listOf(33), sondage.commentaires.map { it.id })
        assertEquals(true, sondage.commentaires.single().peutSupprimer)
    }

    @Test
    fun unCompteIllisibleNEmpechePasDeLireLaFiche() = runTest {
        routes["GET /api/sondages/12"] = 200 to FICHE
        routes["GET /api/auth/me"] = 401 to "{}"
        assertNull(service.sondage(12).moi)
    }

    @Test
    fun uneFicheIntrouvableSeDit() = runTest {
        routes["GET /api/auth/me"] = 200 to MOI
        routes["GET /api/sondages/99"] = 404 to "{}"
        try {
            service.sondage(99)
            fail("introuvable")
        } catch (e: ErreurService) {
            assertEquals(ErreurService.Introuvable, e)
        }
    }

    @Test
    fun leVoteEnvoieLOptionLaPrecisionEtLeCommentaireNettoyes() = runTest {
        routes["POST /api/sondages/12/voter"] = 201 to """{"message": "Vote enregistré"}"""
        service.voter(VoteSondage(option = 4, reponseLibre = "  Le 20 juin ", commentaire = "   ", charteAcceptee = true), 12)
        val recue = recues.single()
        assertTrue(recue, recue.startsWith("POST /api/sondages/12/voter "))
        assertEquals(corps("""{"option_id": 4, "commentaire": null, "reponse_libre": "Le 20 juin"}"""), corps(recue.substringAfter("voter ")))
    }

    @Test
    fun unVoteRefuseDitLaPhraseDuServeur() = runTest {
        routes["POST /api/sondages/12/voter"] = 400 to """{"detail": "Vous avez déjà voté"}"""
        try {
            service.voter(VoteSondage(option = 1, charteAcceptee = true), 12)
            fail("refusé")
        } catch (e: ErreurService) {
            assertEquals(ErreurService.Refus("Vous avez déjà voté", 400), e)
        }
    }

    @Test
    fun commenterEtSupprimerPartentParLeursRoutes() = runTest {
        routes["POST /api/sondages/12/commenter"] = 201 to COMMENTAIRE
        routes["DELETE /api/sondages/12/commentaires/33"] = 204 to ""
        service.commenter("Je viendrai avec des chaises", 12)
        service.supprimerCommentaire(33, 12)
        assertEquals(corps("""{"contenu": "Je viendrai avec des chaises"}"""), corps(recues[0].substringAfter("commenter ")))
        assertEquals("DELETE /api/sondages/12/commentaires/33", recues[1])
    }

    @Test
    fun leSignalementPartAuConseilParSaRoute() = runTest {
        routes["POST /api/signalements"] = 201 to """{"id": 5}"""
        ServiceSignalementsApi(session).signalerAuConseil(CibleSignalement.commentaire(32), "Hors sujet")
        service.signalerAuConseil(CibleSignalement("idee", 4), "Propos blessants")
        assertEquals(
            corps("""{"cible_type": "commentaire", "cible_id": 32, "motif": "Hors sujet"}"""),
            corps(recues[0].substringAfter("signalements ")),
        )
        assertEquals(
            corps("""{"cible_type": "idee", "cible_id": 4, "motif": "Propos blessants"}"""),
            corps(recues[1].substringAfter("signalements ")),
        )
    }

    private companion object {
        const val MOI = """{
          "id": 7, "nom": "Essai", "prenom": "Camille", "email": "camille@residence.example.test",
          "statut": "copropriétaire_résident", "role": "résident", "actif": true, "onboarding_complete": true,
          "onboarding_etape": 0, "preferences_notifications": "{}", "cree_le": "2026-03-02T10:00:00",
          "nom_affiche": "Camille ESSAI", "nom_aide_affiche": ""
        }"""

        const val COMMENTAIRE = """{"id": 34, "auteur_id": 7, "contenu": "Je viendrai avec des chaises",
          "cree_le": "2026-11-15T10:00:00", "auteur_nom": "Camille Essai", "peut_supprimer": true}"""

        const val COMMENTAIRE_33 = """{"id": 33, "auteur_id": 7, "contenu": "J'apporterai des guirlandes.",
          "cree_le": "2026-11-09T20:15:00", "auteur_nom": "Camille Essai", "peut_supprimer": true}"""

        const val FICHE = """{"id": 12, "question": "Quel jour pour la fête des voisins ?",
          "description": "<p>Trois dates.</p>", "cloture": false, "cloture_le": "2026-11-20T18:00:00",
          "cree_le": "2026-11-08T09:00:00", "mon_vote": null, "resultats_publics": true, "resultats_visibles": true,
          "options": [
            {"id": 1, "libelle": "Samedi 6 juin", "ordre": 0, "nb_votes": 14},
            {"id": 4, "libelle": "Une autre date", "ordre": 3, "champ_libre": true, "nb_votes": 2,
             "reponses_libres": ["Plutôt un vendredi soir"]}
          ],
          "commentaires": [$COMMENTAIRE_33]}"""
    }
}
