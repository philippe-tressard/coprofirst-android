// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.annuaire

import fr.coprofirst.app.commun.annuaire.Annuaire
import fr.coprofirst.app.session.AdresseInstallation
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
import org.junit.Assert.assertNull
import org.junit.Test

/** L'annuaire, lu par le client généré comme l'iOS le lit (`ResidenceTests`, « Annuaire »). */
class AnnuaireTest {
    private val serveur = MockWebServer().apply { start() }
    private val service = ServiceAnnuaireApi(
        SessionOuverte(
            Installation("https://residence.exemple.test", "Les Tilleuls"),
            SessionsParHote(OkHttpClient()),
            Dispatchers.Unconfined,
        ) { _: AdresseInstallation -> serveur.url("/api").toString() },
    )

    @After
    fun arreter() = serveur.close()

    @Test
    fun lAnnuaireSeLitDepuisLeJSONDuServeur() = runTest {
        serveur.enqueue(
            MockResponse.Builder().code(200).addHeader("Content-Type", "application/json").body(
                """{"cs": {"ag_annee": 2025, "ag_date": "2025-06-20", "membres": [
                  {"id": 1, "genre": "Mme", "prenom": "Alix", "nom": "Exemple", "nom_affiche": "Alix EXEMPLE",
                   "batiment_nom": "A", "est_gestionnaire_site": false, "est_president": true, "etage": 2,
                   "etage_libelle": "2ᵉ étage"}]},
                 "syndic": {"nom_syndic": "Cabinet", "nom_syndic_source": "contrat", "adresse": "",
                  "membres": [{"id": 9, "genre": "", "prenom": "Elsa", "nom": "Gestion", "nom_affiche": "Elsa GESTION",
                   "telephone": "01 02, 01 02 , 06 07", "telephones": ["01 02", "06 07"], "est_principal": true}]},
                 "whatsapp_url": null}""",
            ).build(),
        )
        val annuaire = service.annuaire()
        assertEquals("GET /api/admin/annuaire", serveur.takeRequest().let { "${it.method} ${it.url.encodedPath}" })
        val membre = annuaire.cs.membres.single()
        assertEquals(true, membre.estPresident)
        // La civilité puis le nom SERVI (`nom_affiche`), comme la carte du site.
        assertEquals("Mme Alix EXEMPLE", membre.nomComplet)
        val contact = annuaire.syndic.membres.single()
        assertEquals("sans civilité : le nom seul", "Elsa GESTION", contact.nomComplet)
        assertEquals("un doublon ne s'affiche qu'une fois", listOf("01 02", "06 07"), contact.telephones)
        assertEquals("EG", contact.initiales)
        assertNull("une adresse vide n'est pas une adresse", annuaire.syndic.adresse)
        assertNull(annuaire.whatsappUrl)
    }

    @Test
    fun leConseilSeGroupeParBatimentSansBatimentEnDernier() {
        val groupes = AnnuaireSimule.ANNUAIRE.copy(
            cs = AnnuaireSimule.ANNUAIRE.cs.copy(membres = listOf(Annuaire.Membre(9, nom = "Sans")) + AnnuaireSimule.ANNUAIRE.cs.membres),
        ).groupesCS
        assertEquals(listOf("A", "B", null), groupes.map { it.first })
        assertEquals(2, groupes.first().second.size)
        assertEquals(2, groupes.last().second.size)
    }
}
