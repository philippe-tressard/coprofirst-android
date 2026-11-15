// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.admin

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.coprofirst.api.modeles.IndicateursTelemetrie
import fr.coprofirst.app.ThemeCoproFirst
import fr.coprofirst.app.commun.Apparence
import fr.coprofirst.app.commun.GestesDeSortie
import fr.coprofirst.app.commun.StockageApparence
import fr.coprofirst.app.commun.onglets.CadreAOnglets
import fr.coprofirst.app.commun.onglets.OngletsSurLeSite
import fr.coprofirst.app.commun.onglets.ReperesOnglets
import fr.coprofirst.app.navigation.BarreDuHaut
import fr.coprofirst.app.navigation.ContexteDeMenu
import fr.coprofirst.app.navigation.DroitsOnglet
import fr.coprofirst.app.navigation.PagesParDefaut
import fr.coprofirst.app.session.Installation
import fr.coprofirst.app.session.SessionOuverte
import fr.coprofirst.app.session.SessionsParHote
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * Le Paramétrage à l'écran, comme l'iOS (`AdminUITests`) : chacun n'y ouvre que ce que son
 * rôle lui ouvre, la Maintenance montre ce que sa route sert, la Télémétrie change de vue,
 * et le reste est « Sur le site ». Repères : les identifiants de l'iOS.
 */
@RunWith(AndroidJUnit4::class)
class AdminInterfaceTest {
    @get:Rule
    val ecran = createComposeRule()

    private val service = ServiceSimule()
    private val administrateur = DroitsOnglet.depuis(listOf("propriétaire", "admin"), null, "copropriétaire_résident", estModerateur = true)
    private val operateur = DroitsOnglet.depuis(listOf("operateur"), null, "admin_technique", estModerateur = false)

    private fun ouvrir(droits: DroitsOnglet) {
        val contexte = ContexteDeMenu(
            page = PagesParDefaut.toutes.first { it.id == OngletsAdmin.PAGE },
            session = SessionOuverte(Installation("https://residence.exemple.test", "Les Glycines"), SessionsParHote()),
            droits = droits,
            sortie = object : GestesDeSortie {
                override fun deconnecter() = Unit
                override fun oublierTout() = Unit
            },
            apparence = Apparence(object : StockageApparence {
                override fun lire(): String? = null
                override fun ecrire(valeur: String) = Unit
            }),
            barre = BarreDuHaut(),
        )
        ecran.setContent { ThemeCoproFirst { CadreAOnglets(contexte, OngletsAdmin.page(service)) } }
    }

    @Test
    fun lAdministrateurLitLExportPasLaSante() {
        ouvrir(administrateur)
        for (id in listOf("telemetry", "maintenance", OngletsSurLeSite.ID)) {
            ecran.onNodeWithTag(ReperesOnglets.onglet(id)).assertExists()
        }
        ecran.onNodeWithTag(ReperesOnglets.onglet("maintenance")).performScrollTo().performClick()
        ecran.waitUntil(5_000) { service.lectures.contains("export") }
        ecran.onNodeWithTag(ReperesAdmin.EXPORT).performScrollTo().assertIsDisplayed()
        ecran.onNodeWithTag(ReperesAdmin.tache("backup")).assertDoesNotExist()
        assertEquals("la santé est à l'opérateur seul", false, service.lectures.contains("sante"))
        ecran.onNodeWithTag(ReperesAdmin.surLeSite("maintenance")).performScrollTo().assertIsDisplayed()

        // Ce que l'app ne porte pas : « Sur le site », avec son lien.
        ecran.onNodeWithTag(ReperesOnglets.onglet(OngletsSurLeSite.ID)).performScrollTo().performClick()
        ecran.onNodeWithTag(ReperesOnglets.surLeSite("imports")).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun lOperateurNOuvreQueLaMaintenance() {
        ouvrir(operateur)
        ecran.onNodeWithTag(ReperesOnglets.onglet("maintenance")).assertIsSelected()
        ecran.onNodeWithTag(ReperesOnglets.onglet("telemetry")).assertDoesNotExist()
        ecran.waitUntil(5_000) { service.lectures.contains("sante") }
        ecran.onNodeWithTag(ReperesAdmin.tache("backup")).performScrollTo().assertIsDisplayed()
        ecran.onNodeWithText("NŒUD-B en retard").performScrollTo().assertIsDisplayed()
        ecran.onNodeWithTag(ReperesAdmin.ANOMALIES).performScrollTo().assertIsDisplayed()
        ecran.onNodeWithTag(ReperesAdmin.EXPORT).assertDoesNotExist()
    }

    @Test
    fun laTelemetrieChangeDeVue() {
        ouvrir(administrateur)
        ecran.onNodeWithTag(ReperesOnglets.onglet("telemetry")).performScrollTo().performClick()
        ecran.waitUntil(5_000) { service.portees.isNotEmpty() }
        ecran.onNodeWithTag(ReperesAdmin.GRAPHE).performScrollTo().assertIsDisplayed()
        ecran.onNodeWithTag(ReperesAdmin.vue(PorteeTelemetrie.Mois)).performClick()
        ecran.waitUntil(5_000) { service.portees.lastOrNull() == PorteeTelemetrie.Mois }
        ecran.onNodeWithTag(ReperesAdmin.vue(PorteeTelemetrie.Mois)).assertIsSelected()
        ecran.onNodeWithTag(ReperesAdmin.FILTRE).performClick()
        ecran.waitUntil(5_000) { service.sans.lastOrNull() == true }
    }

    /** La résidence simulée : des réponses inventées, aucun nom ni machine réels. */
    private class ServiceSimule : ServiceAdmin {
        val lectures = mutableListOf<String>()
        val portees = mutableListOf<PorteeTelemetrie>()
        val sans = mutableListOf<Boolean>()

        override suspend fun santeTaches(): SanteTaches {
            lectures += "sante"
            return SanteTaches(
                listOf(
                    SanteTaches.Tache("backup", "ok", "nœud-a", Instant.parse("2026-11-10T01:00:00Z"), noeudEnRetard = "nœud-b"),
                    SanteTaches.Tache("telemetrie", "erreur", "nœud-a", Instant.parse("2026-11-10T00:15:00Z")),
                ),
                listOf(SanteTaches.Anomalie("telemetrie", "nœud-a", Instant.parse("2026-11-10T00:15:00Z"), "Agrégation interrompue.")),
                7,
            )
        }

        override suspend fun dernierExport(): DernierExport {
            lectures += "export"
            return DernierExport("export-2026-11-09.tar.gz", Instant.parse("2026-11-09T02:40:00Z"), 48_234_496, 152_340, 318)
        }

        override suspend fun tableauTelemetrie(portee: PorteeTelemetrie, sansGestionnaire: Boolean): TableauTelemetrie {
            portees += portee
            sans += sansGestionnaire
            return TableauTelemetrie(
                IndicateursTelemetrie(vues = 146, pages = 11, utilisateurs = 24, heurePointe = "18h"),
                listOf(TableauTelemetrie.Baton("8h", 6, 3), TableauTelemetrie.Baton("9h", 14, 5), TableauTelemetrie.Baton("10h", 9, 4)),
                "Vues par heure",
                listOf(TableauTelemetrie.PageVue("/tableau-de-bord", 58, 21)),
                listOf(TableauTelemetrie.UtilisateurActif("Camille EXEMPLE", "locataire", 34, 9, null)),
                TableauTelemetrie.FiltreGestionnaire(true, true, sansGestionnaire, null),
            )
        }
    }
}
