// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.affaires

import fr.coprofirst.app.commun.perimetres.Perimetre
import fr.coprofirst.app.session.AdresseInstallation
import fr.coprofirst.app.session.ErreurService
import fr.coprofirst.app.session.Installation
import fr.coprofirst.app.session.SessionOuverte
import fr.coprofirst.app.session.SessionsParHote
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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

/** Chaque lecture et chaque geste part par SA route du contrat, par le client généré. */
class ServiceAffairesApiTest {
    private val serveur = MockWebServer().apply { start() }
    private val session = SessionOuverte(
        Installation("https://residence.exemple.test", "Les Glycines"),
        SessionsParHote(OkHttpClient()),
        Dispatchers.Unconfined,
        { _: AdresseInstallation -> serveur.url("/api").toString() },
    )
    private val service = ServiceAffairesApi(session)
    private val recues = mutableListOf<String>()

    @After
    fun arreter() = serveur.close()

    /** Le serveur répond par route ; ce qui n'est pas prévu répond 404. */
    private fun routes(vararg reponses: Pair<String, Pair<Int, String>>) {
        val table = reponses.toMap()
        serveur.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val cle = "${request.method} ${request.url.encodedPath}"
                synchronized(recues) { recues += "$cle ${request.body?.utf8().orEmpty()}".trim() }
                val (code, corps) = table[cle] ?: (404 to """{"detail": "Introuvable"}""")
                return MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(corps).build()
            }
        }
    }

    @Test
    fun laListeEtLesReferentielsSeLisentDuContrat() = runTest {
        routes(
            "GET /api/tickets" to (200 to "[${ticket(3, archivee = true)}, ${ticket(4, colonne = "\"cs\"")}]"),
            "GET /api/tickets/referentiels" to (200 to REFERENTIELS),
        )
        val affaires = service.affaires()
        assertEquals(listOf(3, 4), affaires.map { it.id })
        assertTrue(affaires[0].archivee)
        assertEquals("cs", affaires[1].colonne)
        assertEquals("Bât. A", affaires[1].lieu)
        assertEquals(listOf("Ascenseurs du Parc"), affaires[1].prestataires)
        val r = service.referentiels()
        assertEquals(listOf("cs", "termine"), r.colonnes.map { it.id })
        assertEquals("résolu", r.statutPourColonne("termine")?.code)
    }

    @Test
    fun laRechercheRendLesIdentifiantsSansDoublon() = runTest {
        routes("GET /api/tickets/recherche" to (200 to """[{"ticket_id": 5, "ou": "titre"}, {"ticket_id": 2, "ou": "suite"}, {"ticket_id": 5, "ou": "message"}]"""))
        assertEquals(listOf(5, 2), service.rechercher("fuite"))
        assertTrue(recues.single().startsWith("GET /api/tickets/recherche"))
    }

    @Test
    fun meConcerneSeTaitSurUnEchec() = runTest {
        routes("GET /api/lots/mes-perimetres" to (500 to "{}"))
        assertEquals(false, service.offrirMeConcerne())
        routes("GET /api/lots/mes-perimetres" to (200 to """{"batiments": ["bat-a"], "desservis": []}"""))
        assertEquals(true, service.offrirMeConcerne())
    }

    @Test
    fun deplacerEstUneSuiteDEtatQuiNAvertitPersonne() = runTest {
        routes("POST /api/tickets/4/evolutions" to (201 to EVOLUTION))
        service.deplacer(4, "résolu")
        val corps = recues.single()
        assertTrue(corps, corps.startsWith("POST /api/tickets/4/evolutions"))
        assertTrue(corps, "\"type\":\"etat\"" in corps && "\"nouveau_statut\":\"résolu\"" in corps && "\"notifier\":false" in corps)
    }

    @Test
    fun archiverPoseArchiveManuel() = runTest {
        routes("PATCH /api/tickets/4" to (200 to ticket(4, archivee = true)))
        val lue = service.archiver(4)
        // Rien d'autre ne part : un null envoyé serait appliqué (les affaires liées, #99).
        assertEquals("PATCH /api/tickets/4 {\"archive_manuel\":true}", recues.single())
        assertTrue(lue.archivee)
    }

    @Test
    fun creerEnvoieLaSaisieEnHtmlDuSite() = runTest {
        routes("POST /api/tickets" to (201 to ticket(9)))
        val creee = service.creer(SaisieAffaire("panne", " Fuite ", "Au local\n<vélos>", urgente = true, perimetre = "bat-a"))
        assertEquals(9, creee.id)
        val corps = recues.single()
        assertTrue(corps, "\"titre\":\"Fuite\"" in corps && "\"categorie\":\"panne\"" in corps && "\"urgente\":true" in corps)
        assertTrue(corps, "\"perimetre_cible\":[\"bat-a\"]" in corps && "&lt;vélos&gt;" in corps)
    }

    @Test
    fun lesGestesDeLaFichePartentParLeursRoutes() = runTest {
        routes(
            "POST /api/tickets/4/messages" to (201 to """{"id": 1, "ticket_id": 4, "contenu": "<p>Merci</p>", "interne": false, "cree_le": "2026-11-05T10:00:00Z"}"""),
            "POST /api/tickets/4/evolutions" to (201 to EVOLUTION),
            "PATCH /api/tickets/4" to (200 to ticket(4)),
            "GET /api/perimetres" to (200 to "[]"),
        )
        service.repondre(4, " Merci ")
        service.ajouterSuite(4, SaisieSuite("ouvert", contenu = "Vu"))
        service.ajouterSuite(4, SaisieSuite("ouvert", statut = "en_cours"))
        service.regler(4, TablesAffaires.options.first { it.cle == "epingle" }, true)
        service.regler(4, TablesAffaires.options.first { it.cle == "urgente" }, false)
        assertEquals(emptyList<Perimetre>(), service.perimetres())
        assertTrue(recues[0], recues[0].startsWith("POST /api/tickets/4/messages") && "\"contenu\":\"<p>Merci</p>\"" in recues[0])
        assertTrue(recues[1], "\"type\":\"commentaire\"" in recues[1] && champ(recues[1], "nouveau_statut") == null)
        assertTrue(recues[2], "\"type\":\"etat\"" in recues[2] && "\"nouveau_statut\":\"en_cours\"" in recues[2] && champ(recues[2], "contenu") == null)
        assertTrue(recues[3], "\"epingle\":true" in recues[3])
        assertTrue(recues[4], "\"urgente\":false" in recues[4])
    }

    @Test
    fun corrigerUneSuiteLitCeQueLAffaireNAPasSuivi() = runTest {
        val nonSuivie = EVOLUTION.replace("\"ancien_statut\"", "\"affaire_suivie\": false, \"affaire_suivie_libelle\": \"L'affaire a avancé depuis.\", \"etat_propose\": \"ouvert\", \"ancien_statut\"")
        routes(
            "PATCH /api/tickets/4/evolutions/11" to (200 to nonSuivie),
            "DELETE /api/tickets/4/evolutions/11" to (204 to ""),
        )
        val issue = service.corrigerSuite(4, 11, CorrectionSuite(suivi = CorrectionSuite.Suivi("etat", "en_cours")))
        assertEquals(IssueCorrection("L'affaire a avancé depuis.", "ouvert", null), issue)
        assertTrue(recues[0], "\"type\":\"etat\"" in recues[0] && "\"nouveau_statut\":\"en_cours\"" in recues[0] && champ(recues[0], "contenu") == null)
        service.supprimerSuite(4, 11)
        assertTrue(recues[1], recues[1].startsWith("DELETE /api/tickets/4/evolutions/11"))
    }

    @Test
    fun enregistrerNEnvoieQueLesChampsChanges() = runTest {
        routes("PATCH /api/tickets/4" to (200 to ticket(4)))
        service.enregistrer(4, EditionAffaire(titre = "Fuite d'eau", perimetres = emptyList()))
        assertEquals("PATCH /api/tickets/4 {\"titre\":\"Fuite d'eau\",\"perimetre_cible\":[]}", recues.single())
    }

    /** #182 : les prestataires d'une affaire partent par `prestataire_ids`, dans l'ordre. */
    @Test
    fun lesPrestatairesPartentParLeursIdentifiants() = runTest {
        routes("PATCH /api/tickets/4" to (200 to ticket(4)))
        service.enregistrer(4, EditionAffaire(prestataires = listOf(5, 3)))
        assertEquals("PATCH /api/tickets/4 {\"prestataire_ids\":[5,3]}", recues.single())
    }

    @Test
    fun unRefusDuServeurDitSaPhrase() = runTest {
        routes("PATCH /api/tickets/4" to (403 to """{"detail": "Seul le conseil syndical peut archiver."}"""))
        try {
            service.archiver(4)
            fail("refus attendu")
        } catch (e: ErreurService.Refus) {
            assertEquals("Seul le conseil syndical peut archiver.", e.phraseServie)
        }
    }

    @Test
    fun laFicheLitSesRubriquesEtTolereLesComptesRendusIllisibles() = runTest {
        routes(
            "GET /api/tickets/4" to (200 to ticket(4, close = true)),
            "GET /api/tickets/4/evolutions" to (200 to "[$EVOLUTION]"),
            "GET /api/tickets/4/messages" to (200 to """[{"id": 9, "ticket_id": 4, "contenu": "<p>Merci</p>", "interne": false, "cree_le": "2026-11-05T10:00:00Z", "est_auteur": true}]"""),
            "GET /api/tickets/referentiels" to (200 to REFERENTIELS),
            "GET /api/points-conseil/affaires/4" to (500 to "{}"),
            "GET /api/tickets/4/synthese" to (200 to """{"synthese": {"id": 1, "ticket_id": 4, "statut": "validee", "synthese": "<p>Réparé</p>"}}"""),
        )
        val fiche = service.affaire(4)
        assertEquals("A-0004", fiche.numero)
        assertEquals(1, fiche.suites.size)
        assertEquals("Ouvert → Résolu", fiche.suites.single().changementDEtat(fiche.referentiels))
        assertTrue(fiche.messages.single().deMoi)
        assertNull(fiche.comptesRendus)
        assertEquals(true, fiche.synthese?.validee)
    }

    @Test
    fun uneActualiteNeLitPasDeMessages() = runTest {
        routes(
            "GET /api/tickets/4" to (200 to ticket(4, categorie = "actualite")),
            "GET /api/tickets/4/evolutions" to (200 to "[]"),
            "GET /api/tickets/referentiels" to (200 to REFERENTIELS),
            "GET /api/points-conseil/affaires/4" to (200 to "[]"),
        )
        val fiche = service.affaire(4)
        assertTrue(fiche.estActualite)
        assertTrue(recues.none { it.contains("/messages") })
    }

    /** Un champ du corps JSON d'une requête reçue : `null` s'il est absent. */
    private fun champ(recue: String, cle: String): String? =
        Json.parseToJsonElement(recue.substringAfter(' ').substringAfter(' ')).jsonObject[cle]
            ?.takeUnless { it is JsonNull }?.jsonPrimitive?.content

    private companion object {
        fun ticket(
            id: Int,
            archivee: Boolean = false,
            colonne: String = "null",
            close: Boolean = false,
            categorie: String = "panne",
            prestataires: String = """[{"id": 8, "nom": "Ascenseurs du Parc"}]""",
        ) = """{"id": $id, "numero": "A-000$id", "titre": "Fuite $id", "description": "<p>Une fuite</p>", "categorie": "$categorie",
            "statut": "ouvert", "priorite": "normale", "cree_le": "2026-11-02T09:00:00Z", "urgente": false,
            "categorie_libelle": "Panne", "statut_libelle": "Ouvert", "suivi_actualite_libelle": "", "close": $close,
            "colonne_kanban": $colonne, "frequence_libelle": "", "nouveau": false, "nom_proprietaire": "",
            "perimetre_libelle": "Bât. A", "archivee": $archivee, "suivi_kanban": true, "prestataires": $prestataires,
            "peut_archiver": true, "peut_deplacer_au_kanban": true}"""

        const val EVOLUTION = """{"id": 11, "type": "etat", "cree_le": "2026-11-03T10:00:00Z", "ticket_id": 4,
            "etat_propose_libelle": "", "nouveau_statut_libelle": "", "perimetre_precise_libelle": "", "ancien_statut": "ouvert", "nouveau_statut": "résolu"}"""

        const val REFERENTIELS = """{"categories": [{"code": "panne", "libelle": "Panne", "emoji": "🛠️", "description": "",
            "carnet": true, "reservee": false, "permise": true, "en_tete": false, "sections_essentielles": []}],
            "statuts": [{"code": "ouvert", "libelle": "Ouvert", "clos": false, "proposable": true, "colonne_kanban": "cs"},
                        {"code": "résolu", "libelle": "Résolu", "clos": true, "proposable": true, "colonne_kanban": "termine"}],
            "colonnes_kanban": [{"code": "cs", "libelle": "CS (en cours)", "libelle_court": "CS"},
                                {"code": "termine", "libelle": "Terminé", "libelle_court": "Terminé"}],
            "natures": [{"code": "activite", "libelle": "Affaire"}]}"""
    }
}
