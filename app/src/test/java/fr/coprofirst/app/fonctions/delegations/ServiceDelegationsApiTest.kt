// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.delegations

import fr.coprofirst.app.commun.LibelleTeinte
import fr.coprofirst.app.session.AdresseInstallation
import fr.coprofirst.app.session.ErreurService
import fr.coprofirst.app.session.Installation
import fr.coprofirst.app.session.SessionOuverte
import fr.coprofirst.app.session.SessionsParHote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate

/**
 * Chaque geste des Délégations part par SA route du contrat, par le client généré, et se
 * lit comme l'iOS le lit (`FixturesDelegations`). Tout est inventé : aucun nom réel.
 */
class ServiceDelegationsApiTest {
    private val serveur = MockWebServer().apply { start() }
    private val base = { _: AdresseInstallation -> serveur.url("/api").toString() }
    private val session = SessionOuverte(
        Installation("https://residence.exemple.test", "Les Glycines"),
        SessionsParHote(OkHttpClient()),
        Dispatchers.Unconfined,
        base,
    )
    private val service = ServiceDelegationsApi(session)

    @After
    fun arreter() = serveur.close()

    private fun repondre(code: Int, corps: String) = serveur.enqueue(
        MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(corps).build(),
    )

    private fun requete(): String = serveur.takeRequest().let { "${it.method} ${it.url.encodedPath}" }

    @Test
    fun laListeSeLitDuContratAvecSesGestesServis() = runTest {
        repondre(200, "[$EN_ATTENTE, $REVOQUEE]")
        val liste = service.delegations()
        assertEquals("GET /api/delegations", requete())
        assertEquals(
            Delegation(
                12, "Lucie EXEMPLE", "Camille ESSAI", "en_attente", "Assistance pendant une convalescence",
                "2026-11-06", "2027-02-04", peutAccepter = true, peutRevoquer = true,
            ),
            liste[0],
        )
        // Sans `peut_*` servis : aucun geste — l'app ne les déduit jamais du statut.
        assertEquals(Delegation(5, "Paul FICTIF", "Marc DÉMO", "revoquee", "", "2025-10-04", null), liste[1])
    }

    @Test
    fun lesGestesPartentParLeursRoutes() = runTest {
        repondre(200, EN_ATTENTE.replace("en_attente", "active").replace("\"peut_accepter\": true", "\"peut_accepter\": false"))
        val acceptee = service.accepter(12)
        assertEquals("POST /api/delegations/12/accepter", requete())
        assertEquals("active", acceptee.statut)
        assertFalse("la réponse porte les gestes d'après", acceptee.peutAccepter)
        repondre(200, REVOQUEE)
        service.revoquer(5)
        assertEquals("POST /api/delegations/5/revoquer", requete())
    }

    @Test
    fun laCreationEnvoieLeCorpsDuContrat() = runTest {
        repondre(201, EN_ATTENTE)
        service.creer(SaisieDelegation(mandant = 3, aidant = 7, motif = "  Assistance  ", dateFin = LocalDate.of(2027, 2, 4)))
        val envoi = serveur.takeRequest()
        assertEquals("POST /api/delegations", "${envoi.method} ${envoi.url.encodedPath}")
        val corps = envoi.body?.utf8().orEmpty()
        for (attendu in listOf("\"mandant_id\":3", "\"aidant_id\":7", "\"motif\":\"Assistance\"", "\"date_fin\":\"2027-02-04\"")) {
            assertTrue("$attendu dans $corps", attendu in corps)
        }
    }

    @Test
    fun lesResidentsSeLisentParLeurNomServi() = runTest {
        repondre(200, "[${resident(3, "Lucie", "Exemple", "Lucie EXEMPLE", actif = true)}, ${resident(9, "Paul", "Fictif", "", actif = false)}]")
        val residents = service.residentsDelegables()
        assertEquals("GET /api/admin/utilisateurs", requete())
        assertEquals(ResidentDelegable(3, "Lucie EXEMPLE", "lucie@exemple.test", true), residents[0])
        assertEquals("prénom puis nom quand le nom affiché manque", "Paul Fictif", residents[1].nomAffiche)
        assertEquals("seuls les comptes actifs se lient", listOf(3), residents.liables().map { it.id })
        assertEquals("Lucie EXEMPLE (lucie@exemple.test)", residents[0].choix)
    }

    @Test
    fun unRefusDitLaPhraseDuServeur() = runTest {
        repondre(400, """{"detail": "Une délégation active existe déjà."}""")
        try {
            service.creer(SaisieDelegation(3, 7))
            fail("refusé")
        } catch (e: ErreurService) {
            assertEquals(ErreurService.Refus("Une délégation active existe déjà.", 400), e)
        }
    }

    @Test
    fun laSaisieSuitLaRegleDuSite() {
        assertFalse(SaisieDelegation().complete)
        assertFalse(SaisieDelegation(mandant = 3).complete)
        assertFalse("deux personnes distinctes", SaisieDelegation(3, 3).complete)
        assertTrue(SaisieDelegation(3, 7).complete)
        assertEquals("l'aidant devenu mandant se retire", null, SaisieDelegation(3, 7).avecMandant(7).aidant)
        assertEquals(7, SaisieDelegation(3, 7).avecMandant(4).aidant)
        assertEquals("sans date : sans limite", null, SaisieDelegation(3, 7).corps().dateFin)
    }

    @Test
    fun lEtatSeLitDansLaTableDuSite() {
        val active = Delegation(1, "A", "B", "active", "", "2026-01-01", null)
        assertEquals(LibelleTeinte("Active", "badge-green"), active.libelleStatut)
        assertEquals("un état inconnu s'affiche brut", LibelleTeinte.brut("suspendue"), active.copy(statut = "suspendue").libelleStatut)
    }

    private companion object {
        const val EN_ATTENTE = """{"id": 12, "mandant_id": 3, "mandant_nom": "Lucie EXEMPLE", "aidant_id": 7,
            "aidant_nom": "Camille ESSAI", "statut": "en_attente", "motif": "Assistance pendant une convalescence",
            "date_debut": "2026-11-06", "date_fin": "2027-02-04", "cree_le": "2026-11-06T09:00:00",
            "revoque_le": null, "peut_accepter": true, "peut_revoquer": true}"""
        const val REVOQUEE = """{"id": 5, "mandant_id": 9, "mandant_nom": "Paul FICTIF", "aidant_id": 4,
            "aidant_nom": "Marc DÉMO", "statut": "revoquee", "motif": "", "date_debut": "2025-10-04",
            "cree_le": "2025-10-04T08:00:00", "revoque_le": "2026-10-09T17:45:00"}"""

        fun resident(id: Int, prenom: String, nom: String, affiche: String, actif: Boolean) = """
            {"id": $id, "prenom": "$prenom", "nom": "$nom", "nom_affiche": "$affiche",
             "email": "${prenom.lowercase()}@exemple.test", "actif": $actif, "role": "propriétaire",
             "statut": "copropriétaire_résident", "cree_le": "2025-03-02T10:00:00", "onboarding_complete": true,
             "onboarding_etape": 4, "preferences_notifications": "{}", "has_lots": true, "has_bail": false,
             "has_vigik": true, "has_tc": false,
             "etiquettes": {"loti": "oui", "bail": "non", "vigik": "oui", "tc": "non"},
             "nom_aide_affiche": "", "gestes_roles": {"admin": null, "conseil_syndical": null, "operateur": null}}
        """
    }
}
