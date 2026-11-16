// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.residence

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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.Collections

/**
 * Le service réel, par le client GÉNÉRÉ, contre des réponses à la forme des routes
 * (dates SANS fuseau, comme le serveur les sert) : chaque réponse se lit comme une vraie,
 * chaque geste part sur sa route. Données inventées, hôtes en `.test`.
 */
class ServiceResidenceTest {
    /** Les routes en échec, pour éprouver la tolérance de la Fiche. */
    private val enEchec = mutableSetOf<String>()
    private val recues: MutableList<String> = Collections.synchronizedList(mutableListOf())
    private val serveur = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val route = request.url.encodedPath.removePrefix("/api")
                val requete = request.url.encodedQuery?.let { "$route?$it" } ?: route
                recues += "${request.method} $requete ${request.body?.utf8().orEmpty()}".trim()
                val (code, corps) = if (route in enEchec) 500 to """{"detail": "Erreur"}""" else repondre(request.method, route, request.url.queryParameter("categorie"), request.url.queryParameter("annexes_de"))
                return MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(corps).build()
            }
        }
        start()
    }
    private val service = ServiceResidenceApi(
        SessionOuverte(
            Installation("https://residence.exemple.test", "Les Tilleuls"),
            SessionsParHote(OkHttpClient()),
            Dispatchers.Unconfined,
        ) { _: AdresseInstallation -> serveur.url("/api").toString() },
    )

    @After
    fun arreter() = serveur.close()

    private fun repondre(methode: String, route: String, categorie: String?, annexes: String?): Pair<Int, String> = when ("$methode $route") {
        "GET /copropriete" -> 200 to COPROPRIETE
        "GET /copropriete/batiments" -> 200 to """[{"id": 1, "numero": "A", "nb_etages": 5, "nb_parkings": 18, "nb_caves": 24, "nb_appartements": 24}]"""
        "GET /regles-residence" -> 200 to """[{"id": 3, "titre": "Tri des déchets", "contenu": "<p>Bacs jaunes.</p>", "ordre": 1}]"""
        "GET /diagnostics/types" -> 200 to DIAGNOSTICS
        "GET /documents" -> 200 to when {
            categorie == "plan_residence" -> "[${document(10, "Plan de masse", batiment = 1)}]"
            categorie == "pv_ag" -> "[${document(30, "PV de l'AG", annee = 2026, dateAg = "2026-06-12")}]"
            categorie != null -> "[]"
            annexes == "regle" -> "[${document(40, "Calendrier", regle = 3)}]"
            else -> "[]"
        }
        "GET /carnet-entretien" -> 200 to CARNET
        "GET /equipements" -> 200 to """[{"id": 1, "code": "chaufferie", "libelle": "Chaufferie", "icone": "flame", "ordre": 1, "actif": true, "perimetres_libelle": ""}]"""
        "GET /perimetres" -> 200 to "[${perimetre("bat-a", "Bâtiment A", true)}, ${perimetre("ancien", "Ancien", false)}]"
        "GET /reglement/acces" -> 200 to """{"acces": false}"""
        "GET /reglement" -> 200 to """{"disponible": true, "texte_disponible": false}"""
        "GET /reglement/questions" -> 200 to "[$QUESTION]"
        "POST /reglement/questions" -> 201 to QUESTION
        else -> 404 to """{"detail": "Not Found"}"""
    }

    @Test
    fun laFicheSeLitEnUneVague() = runTest {
        val contenu = service.fiche()
        assertEquals("Résidence Les Tilleuls", contenu.fiche.nom)
        assertEquals("AA0-000-000", contenu.fiche.immatriculation)
        assertTrue(contenu.fiche.syndicEchu)
        assertEquals("30/06/2026", fr.coprofirst.app.commun.formats.FormatsDuSite.dateCourte(contenu.fiche.syndicEcheance))
        assertEquals(listOf(Batiment(1, "A", 18, 24, 24, 0)), contenu.batiments)
        assertEquals(listOf("Plan de masse"), contenu.plans.map { it.titre })
        assertEquals(NumeroBatiment.Connu("A"), contenu.numeroBatiment(contenu.plans[0].batimentId))
        assertEquals(2026, contenu.comptesRendus[0].annee)
        assertEquals(listOf(40), contenu.annexesDeRegle(3).map { it.id })
        // Les non applicables restent servis, mais ne se montrent pas.
        assertEquals(listOf("Diagnostic technique global"), contenu.diagnosticsApplicables.map { it.nom })
        assertEquals("<p>Bon état.</p>", contenu.diagnostics[0].rapports[0].synthese)
        // Chaque rubrique a sa catégorie, chaque annexe sa fiche.
        listOf("categorie=plan_residence", "categorie=reglement_copropriete", "categorie=pv_ag", "annexes_de=document", "annexes_de=regle", "annexes_de=diagnostic")
            .forEach { q -> assertTrue("GET /documents?$q", recues.any { it == "GET /documents?$q" }) }
    }

    @Test
    fun uneRubriqueEnEchecNeVidePasLesAutres() = runTest {
        enEchec += setOf("/copropriete/batiments", "/regles-residence", "/diagnostics/types")
        val contenu = service.fiche()
        assertTrue(contenu.batiments.isEmpty() && contenu.regles.isEmpty() && contenu.diagnostics.isEmpty())
        assertEquals(1, contenu.plans.size)
    }

    @Test
    fun laFicheEnEchecDitSonErreur() = runTest {
        enEchec += "/copropriete"
        try {
            service.fiche()
            fail("la fiche ne se lit pas : l'erreur remonte")
        } catch (_: ErreurService) {
        }
    }

    @Test
    fun leCarnetSeRangeParEquipementEtSeFiltreParPerimetre() = runTest {
        val carnet = service.carnet("bat-a")
        assertTrue(recues.any { it == "GET /carnet-entretien?perimetre=bat-a" })
        assertEquals(listOf("Chaufferie", "ascenseur", null), carnet.groupes.map { it.titre })
        assertEquals(2, carnet.groupes[0].entrees.size)
        assertEquals(listOf("Contrat d'entretien de l'ascenseur"), carnet.enAlerte.map { it.libelle })
        assertNull("une chaîne vide servie n'est pas un détail", carnet.entrees[1].detail)
        // Le modèle commun (#83), lu par `Perimetre.lus` : actifs seulement.
        assertEquals(listOf("bat-a" to "Bâtiment A"), service.perimetres().map { it.code to it.libelle })
    }

    @Test
    fun leReglementSeLitEtUneQuestionSePose() = runTest {
        assertFalse(service.accesReglement())
        val (etat, questions) = service.reglement()
        assertEquals(EtatReglement(disponible = true, texteDisponible = false), etat)
        val question = questions.single()
        assertEquals("<p>Oui, sauf.</p>", question.reponseSansRenvois)
        assertEquals(listOf("R2"), question.renvoisSansExtrait)
        assertTrue(question.avecRenvois)
        assertEquals("badge-green", question.badgeDuVerdict)
        val posee = service.poser("Puis-je fermer mon balcon ?")
        assertEquals(7, posee.id)
        assertTrue(recues.any { it == """POST /reglement/questions {"question":"Puis-je fermer mon balcon ?"}""" })
    }

    private companion object {
        val COPROPRIETE = """{"id": 1, "nom": "Résidence Les Tilleuls", "adresse": "12 allée des Tilleuls, 00000 Ville-Exemple",
            "exercice_libelle": "2026", "numero_immatriculation": "AA0-000-000", "nb_lots_total": 112, "nb_lots_principaux": 48,
            "assurance_compagnie": "Mutuelle Exemple", "assurance_echeance": "2027-03-31", "assurance_reconduit": true,
            "syndic_cabinet": "Cabinet Exemple Gestion", "syndic_echeance": "2026-06-30", "syndic_echu": true,
            "photo_url": "", "nb_parkings_communs": 4}"""

        val DIAGNOSTICS = """[
            {"id": 1, "code": "dtg", "nom": "Diagnostic technique global", "texte_legislatif": "Article L.731-1.", "ordre": 1,
             "frequence": "Tous les 10 ans", "rapports": [{"id": 5, "diagnostic_type_id": 1, "titre": "DTG", "fichier_nom": "dtg.pdf",
             "mime_type": "application/pdf", "publie_le": "2024-11-05T09:00:00", "date_rapport": "2024-11-04", "synthese": "<p>Bon état.</p>"}]},
            {"id": 3, "code": "termites", "nom": "Termites", "texte_legislatif": "Hors zone.", "ordre": 3, "non_applicable": true}]"""

        val CARNET = """{"total": 3, "groupes": [], "annees": ["2026"], "entrees": [
            {"date": "2026-10-28", "libelle": "Révision de la chaudière", "origine": "intervention", "categorie_libelle": "", "perimetre_libelle": "", "equipement": "chaufferie", "detail": "Brûleur."},
            {"date": "2026-09-15", "libelle": "Contrat d'entretien de l'ascenseur", "origine": "contrat", "categorie_libelle": "", "perimetre_libelle": "", "equipement": "ascenseur", "detail": "", "alerte": "visite attendue"},
            {"date": "2026-08-02", "libelle": "Fuite", "origine": "affaire", "categorie_libelle": "", "perimetre_libelle": "", "equipement": "chaufferie", "lien": "/tickets/29", "numero": "A-0029"},
            {"date": "2025-12-03", "libelle": "Luminaire", "origine": "affaire", "categorie_libelle": "", "perimetre_libelle": ""}]}"""

        val QUESTION = """{"id": 7, "texte_id": 1, "question": "Q ?", "reponse": "<p>Oui [R1], sauf [R2].</p>",
            "reponse_sans_renvois": "<p>Oui, sauf.</p>", "renvois_sans_extrait": ["R2"], "verdict": "oui", "verdict_libelle": "Oui",
            "verdict_ton": "succes", "texte_en_vigueur": true, "cree_le": "2026-11-10T08:00:00",
            "extraits": [{"citation": "C", "verifie": true, "renvoi": "R1"}]}"""

        fun document(id: Int, titre: String, batiment: Int? = null, annee: Int? = null, dateAg: String? = null, regle: Int? = null) =
            """{"id": $id, "titre": "$titre", "fichier_nom": "f$id.pdf", "mime_type": "application/pdf", "perimetre": "",
               "publie_le": "2026-01-01T10:00:00", "perimetre_libelle": "", "batiment_id": ${batiment ?: "null"},
               "annee": ${annee ?: "null"}, "date_ag": ${dateAg?.let { "\"$it\"" } ?: "null"}, "regle_residence_id": ${regle ?: "null"}}"""

        fun perimetre(code: String, libelle: String, actif: Boolean) =
            """{"id": 1, "code": "$code", "parent": null, "libelle": "$libelle", "libelle_court": "", "description": "", "icone": null,
               "batiment_id": null, "profondeur": 0, "ordre": 1, "actif": $actif, "portee_globale": false, "concerne_tous": false,
               "selectionnable": true, "privatif": false, "hors_copropriete": false, "utilise": true}"""
    }
}
