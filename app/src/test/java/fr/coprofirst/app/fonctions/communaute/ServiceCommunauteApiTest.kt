// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.communaute

import fr.coprofirst.app.session.AdresseInstallation
import fr.coprofirst.app.session.ErreurService
import fr.coprofirst.app.session.Installation
import fr.coprofirst.app.session.SessionOuverte
import fr.coprofirst.app.session.SessionsParHote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Chaque lecture et chaque geste partent par SA route du contrat, par le client généré. */
class ServiceCommunauteApiTest {
    private val serveur = MockWebServer().apply { start() }
    private val service = ServiceCommunauteApi(
        SessionOuverte(
            Installation("https://residence.exemple.test", "Les Glycines"),
            SessionsParHote(OkHttpClient()),
            Dispatchers.Unconfined,
        ) { _: AdresseInstallation -> serveur.url("/api").toString() },
    )

    @After
    fun arreter() = serveur.close()

    private fun repondre(code: Int, corps: String = "{}") = serveur.enqueue(
        MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(corps).build(),
    )

    private fun requete(): Pair<String, String> =
        serveur.takeRequest().let { "${it.method} ${it.url.encodedPath}" to it.body?.utf8().orEmpty() }

    private fun corps(texte: String): JsonObject = Json.parseToJsonElement(texte).jsonObject

    @Test
    fun leMotifDeRefusEstCeluiDuServeur() = runTest {
        repondre(
            200,
            """{"id": 3, "nom": "Durand", "prenom": "Camille", "email": "c@residence.exemple.test", "statut": "copropriétaire_résident",
               "role": "résident", "actif": true, "onboarding_complete": true, "onboarding_etape": 0,
               "preferences_notifications": "{}", "cree_le": "2026-01-01T00:00:00Z",
               "nom_affiche": "Camille Durand", "nom_aide_affiche": "", "communaute_motif_refus": "Suspendu jusqu'au 20/11."}""",
        )
        val moi = service.moi()
        assertEquals("GET /api/auth/me", requete().first)
        assertEquals(3, moi.id)
        assertEquals("Suspendu jusqu'au 20/11.", moi.motifRefus)
    }

    @Test
    fun leDroitDeCreerUnSondageEstServiEtIllisibleIlVautNon() = runTest {
        repondre(200, """{"peut_creer": true}""")
        assertTrue(lirePeutCreer(service))
        assertEquals("GET /api/sondages/gestes", requete().first)
        repondre(404, """{"detail": "Not Found"}""")
        assertFalse(lirePeutCreer(service))
        repondre(500)
        assertFalse(lirePeutCreer(service))
    }

    @Test
    fun lesListesSeLisentDuContrat() = runTest {
        repondre(200, """[{"id": 4, "question": "Quel jour ?", "resultats_publics": true, "cree_le": "2026-11-15T08:30:00",
                         "perimetre_libelle": "", "nb_votants": 3, "cloture": false}]""")
        assertEquals(listOf(4), service.sondages().map { it.id })
        assertEquals("GET /api/sondages", requete().first)
        repondre(200, """[{"id": 9, "titre": "Vélos", "description": "<p>x</p>", "statut": "ouverte", "cree_le": "2026-11-15T08:30:00Z",
                         "perimetre_libelle": "", "nb_votes": 4, "mon_vote": true}]""")
        assertEquals(4, service.idees().single().nbVotes)
        assertEquals("GET /api/idees", requete().first)
        repondre(200, """[{"id": 2, "titre": "Lave-linge", "description": "", "type_annonce": "vente", "categorie": "electromenager",
                         "statut": "reserve", "auteur_id": 1, "cree_le": "2026-11-15T08:30:00Z", "perimetre_libelle": "",
                         "prix": 120.5, "peut_supprimer": true}]""")
        assertEquals(true, service.annonces().single().peutSupprimer)
        assertEquals("GET /api/annonces", requete().first)
        repondre(200, """{"avis": [], "gestionnaire": true, "mon_avis_id": 6}""")
        val avis = service.avis()
        assertEquals(6, avis.monAvisId)
        assertTrue(avis.gestionnaire)
        assertEquals("GET /api/avis", requete().first)
    }

    @Test
    fun chaqueGestePartParSaRoute() = runTest {
        repondre(201, """{"id": 10}""")
        service.proposerIdee("Vélos", "<p>Des vélos</p>")
        val (idee, corpsIdee) = requete()
        assertEquals("POST /api/idees", idee)
        assertEquals("Vélos", corps(corpsIdee)["titre"]!!.jsonPrimitive.content)

        repondre(201, """{"message": "Vote retiré"}""")
        assertEquals("Vote retiré", service.voterIdee(9))
        assertEquals("POST /api/idees/9/voter", requete().first)

        repondre(200, """{"id": 3, "titre": "T", "description": "", "type_annonce": "don", "categorie": "divers", "statut": "en_cours",
                         "auteur_id": 1, "cree_le": "2026-11-15T08:30:00Z", "perimetre_libelle": ""}""")
        service.deposerAnnonce(SaisieAnnonce(titre = " T ", description = "D", type = "don", prix = "12", coordonneesVisibles = false))
        val (annonce, corpsAnnonce) = requete()
        assertEquals("POST /api/annonces", annonce)
        val envoye = corps(corpsAnnonce)
        assertEquals("T", envoye["titre"]!!.jsonPrimitive.content)
        assertNull("pas de prix pour un don", envoye["prix"]?.jsonPrimitive?.content?.takeIf { it != "null" })
        assertFalse(envoye["contact_visible"]!!.jsonPrimitive.boolean)

        repondre(200, "{}")
        service.changerStatutAnnonce(3, "vendu")
        val (statut, corpsStatut) = requete()
        assertEquals("PATCH /api/annonces/3/statut", statut)
        assertEquals("vendu", corps(corpsStatut)["statut"]!!.jsonPrimitive.content)

        repondre(200, "{}")
        service.supprimerAnnonce(3)
        assertEquals("DELETE /api/annonces/3", requete().first)

        val avis = """{"id": 6, "note": 2, "titre": "T", "description": "", "cree_le": "2026-11-15T08:30:00Z"}"""
        repondre(201, avis)
        service.deposerAvis(SaisieAvis(note = 2, titre = "T", description = "D", remarque = "Lent", consentement = true))
        val (depot, corpsDepot) = requete()
        assertEquals("POST /api/avis", depot)
        assertEquals(2, corps(corpsDepot)["note"]!!.jsonPrimitive.int)
        assertEquals("Lent", corps(corpsDepot)["remarque"]!!.jsonPrimitive.content)
        assertFalse("pas d'accord de diffusion sous le seuil", "consentement_publication" in corps(corpsDepot))

        repondre(200, avis)
        service.corrigerAvis(6, SaisieAvis(note = 5, titre = "T", description = "D", consentement = true))
        val (correction, corpsCorrection) = requete()
        assertEquals("PATCH /api/avis/6", correction)
        assertTrue(corps(corpsCorrection)["consentement_publication"]!!.jsonPrimitive.boolean)
        // Seuls les champs de la saisie partent : la remarque, que la note 5 n'exige pas, est
        // OMISE — jamais envoyée à null, que le serveur appliquerait (#99), comme l'iOS.
        assertEquals(setOf("note", "titre", "description", "consentement_publication"), corps(corpsCorrection).keys)

        repondre(204, "")
        service.archiverAvis(6, archive = true)
        val (archivage, corpsArchivage) = requete()
        assertEquals("PATCH /api/avis/6/archivage", archivage)
        assertTrue(corps(corpsArchivage)["archivee"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun unRefusDitLaPhraseDuServeur() = runTest {
        repondre(403, """{"detail": "Réservé à son auteur ou au conseil syndical."}""")
        try {
            service.supprimerAnnonce(1)
            fail("refusé")
        } catch (e: ErreurService) {
            assertEquals(ErreurService.Refus("Réservé à son auteur ou au conseil syndical.", 403), e)
        }
    }
}
