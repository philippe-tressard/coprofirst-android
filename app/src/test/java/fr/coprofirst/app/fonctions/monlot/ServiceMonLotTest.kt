// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.monlot

import fr.coprofirst.app.commun.acces.TypeAcces
import fr.coprofirst.app.navigation.DroitsOnglet
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
import org.junit.Test

/**
 * Le service réel, par le client GÉNÉRÉ, contre la résidence simulée : chaque réponse de
 * `FixturesMonLot` se lit comme une vraie (le garde-fou du bogue iOS du 05/11/2026), et
 * chaque geste part sur la bonne route.
 */
class ServiceMonLotTest {
    private val residence = ResidenceSimulee()
    private val serveur = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val (code, corps) = residence.repondre(request.method, request.url.encodedPath, request.body?.utf8().orEmpty())
                return MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(corps).build()
            }
        }
        start()
    }
    private val service = ServiceMonLotApi(
        SessionOuverte(
            Installation("https://residence.test", "Les Glycines"),
            SessionsParHote(OkHttpClient()),
            Dispatchers.Unconfined,
        ) { _: AdresseInstallation -> serveur.url("/api").toString() },
    )

    @After
    fun arreter() = serveur.close()

    @Test
    fun lesReponsesSeLisentParLesTypesGeneres() = runTest {
        val lots = service.mesLots()
        assertEquals(listOf(15, 417, 450), lots.map { it.id })
        assertEquals("Appartement – T3", lots[0].typeLu)
        assertEquals("2ème", lots[0].etageLu)
        assertEquals("Bâtiment A", lots[0].perimetres("situe_dans"))
        assertEquals(64.5, lots[0].superficie)
        assertEquals(listOf("vigik", "vigik", "vigik"), service.mesAcces(TypeAcces.Vigik).map { it.type })
        assertEquals(listOf("telecommande"), service.mesAcces(TypeAcces.Telecommande).map { it.type })
        assertEquals(listOf("vigik"), service.accesRecus().map { it.type })
        val baux = service.baux(tous = true)
        assertEquals(listOf("2025-09-01", "2023-02-15", "2021-03-01"), baux.map { it.dateEntree })
        assertEquals("Locataire DU LOT 15", baux[0].nomLocataire)
        assertEquals(listOf("cle"), baux[0].objets.map { it.type })
    }

    @Test
    fun lesDroitsSeLisentDansLeProfilServi() = runTest {
        assertTrue(service.droits().conseil)
        residence.moi = FixturesMonLot.moiBailleur
        assertTrue(service.droits().bailleur)
        residence.moi = FixturesMonLot.moiLocataire
        assertTrue(service.droits().locataire)
    }

    /** « Pas de bail » est un 200 qui porte `null`, ou un 404 : ni l'un ni l'autre n'est une erreur. */
    @Test
    fun sansBailLeLocataireNEstPasEnErreur() = runTest {
        assertNull(service.monBail())
        residence.monBail = "" // une installation qui répond 200 sans corps
        assertNull(service.monBail())
        residence.monBail = FixturesMonLot.MON_BAIL
        val bail = service.monBail()!!
        assertEquals("Sacha EXEMPLE", bail.nomBailleur)
        assertEquals("Bât. A / 15", bail.lot.intitule)
        assertEquals("2ème", bail.lot.etageLu)
    }

    @Test
    fun lesGestesPartentSurLeursRoutes() = runTest {
        service.signalerPerdu(AccesDetenu(21, "TC-2048", "actif", "telecommande"))
        service.signalerPerdu(AccesDetenu(12, "A4F21C08", "actif", "vigik"))
        assertEquals("perdu", service.mesAcces(TypeAcces.Vigik).first { it.id == 12 }.statut)
        assertTrue(service.declarer(TypeAcces.Vigik, "IMPORT-1"))
        assertFalse(service.declarer(TypeAcces.Telecommande, "TC-7777"))
        service.demander(DemandeAcces(TypeAcces.Telecommande, 450, 2, "  "))
        service.recupererAcces(3)
        val gestes = residence.recues.filterNot { it.startsWith("GET") }
        assertEquals("PATCH /api/acces/telecommandes/21/perdu {\"raison\":\"\"}", gestes[0])
        assertTrue(gestes[1].startsWith("PATCH /api/acces/vigiks/12/perdu"))
        assertTrue(gestes[2], gestes[2].contains("\"type\":\"vigik\"") && gestes[2].contains("\"code\":\"IMPORT-1\""))
        // Un motif blanc ne part pas (un null ne s'envoie plus, #99).
        assertEquals("POST /api/acces/commandes {\"lot_id\":450,\"type\":\"telecommande\",\"quantite\":2}", gestes[4])
        assertTrue(gestes[5].startsWith("POST /api/bailleur/baux/3/recuperer-acces"))
    }

    /** Un refus du serveur s'affiche avec SA phrase. */
    @Test
    fun unRefusDitLaPhraseDuServeur() = runTest {
        val voisin = runCatching { service.demander(DemandeAcces(lotId = 999)) }.exceptionOrNull()
        assertEquals(ErreurService.Refus("Vous n'êtes pas associé à ce lot", 403), voisin)
        val deja = runCatching { service.declarer(TypeAcces.Vigik, "A4F21C07") }.exceptionOrNull()
        assertEquals(ErreurService.Refus("Vigik déjà enregistré sur votre compte", 409), deja)
    }

    /** L'échec des lots ne bloque pas l'écran ; le bail et les baux suivent les droits. */
    @Test
    fun lesDonneesSuiventCeQueLeCompteEst() = runTest {
        val conseil = DonneesMonLot.lire(service, DroitsOnglet())
        assertEquals(3, conseil.baux.size)
        assertNull(conseil.bail)
        assertEquals(listOf("lots", "badges", "telecommandes", "location"), conseil.onglets.map { it.id })
        residence.moi = FixturesMonLot.moiLocataire
        residence.monBail = FixturesMonLot.MON_BAIL
        val locataire = DonneesMonLot.lire(service, DroitsOnglet())
        assertEquals(emptyList<Bail>(), locataire.baux)
        assertEquals(3, locataire.bail?.id)
        assertEquals(listOf("lots", "badges", "telecommandes"), locataire.onglets.map { it.id })
        assertEquals(1, DonneesAcces.lire(service, locataire.droits).recus.size)
        assertEquals(0, DonneesAcces.lire(service, conseil.droits).recus.size)
        serveur.close()
        val horsLigne = DonneesMonLot.lire(service, DroitsOnglet(cs = true))
        assertTrue(horsLigne.droits.conseil)
        assertEquals(ErreurService.Injoignable, horsLigne.erreurLots)
    }
}
