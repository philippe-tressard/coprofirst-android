// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.admin

import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isOn
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToString
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.coprofirst.api.modeles.IndicateursTelemetrie
import fr.coprofirst.app.ThemeCoproFirst
import fr.coprofirst.app.commun.Apparence
import fr.coprofirst.app.commun.GestesDeSortie
import fr.coprofirst.app.commun.PhraseAvis
import fr.coprofirst.app.commun.StockageApparence
import fr.coprofirst.app.commun.TonAvis
import fr.coprofirst.app.commun.amenerTag
import fr.coprofirst.app.commun.attendreQue
import fr.coprofirst.app.commun.attendreTexte
import fr.coprofirst.app.commun.onglets.CadreAOnglets
import fr.coprofirst.app.commun.onglets.OngletsSurLeSite
import fr.coprofirst.app.commun.onglets.ReperesOnglets
import fr.coprofirst.app.commun.residenceSimulee
import fr.coprofirst.app.commun.toucher
import fr.coprofirst.app.navigation.BarreDuHaut
import fr.coprofirst.app.navigation.ContexteDeMenu
import fr.coprofirst.app.navigation.DroitsOnglet
import fr.coprofirst.app.navigation.PagesParDefaut
import fr.coprofirst.app.session.SessionOuverte
import fr.coprofirst.app.session.SessionsParHote
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Le Paramétrage à l'écran, comme l'iOS (`AdminUITests`) : chacun n'y ouvre que ce que son
 * rôle lui ouvre, la Maintenance montre ce que sa route sert, la Télémétrie change de vue,
 * et le reste est « Sur le site ». Repères : les identifiants de l'iOS.
 */
@RunWith(AndroidJUnit4::class)
class AdminInterfaceTest {
    // La règle v2 (StandardTestDispatcher) : les effets se mettent en file au lieu de partir
    // aussitôt ; la v1 laissait le retour d'un chargement reprendre sur un autre fil (#114).
    @get:Rule
    val ecran = createComposeRule()

    private val service = ServiceSimule()
    private val administrateur = DroitsOnglet.depuis(listOf("propriétaire", "admin"), null, "copropriétaire_résident", estModerateur = true)
    private val operateur = DroitsOnglet.depuis(listOf("operateur"), null, "admin_technique", estModerateur = false)

    private fun ouvrir(droits: DroitsOnglet) {
        val contexte = ContexteDeMenu(
            page = PagesParDefaut.toutes.first { it.id == OngletsAdmin.PAGE },
            session = SessionOuverte(residenceSimulee("https://residence.exemple.test", "Les Glycines"), SessionsParHote()),
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
        ecran.onNodeWithTag(ReperesOnglets.onglet("maintenance")).toucher()
        ecran.waitUntil(DELAI_MS) { service.lectures.contains("export") }
        ecran.onNodeWithTag(ReperesAdmin.EXPORT).performScrollTo().assertIsDisplayed()
        ecran.onNodeWithTag(ReperesAdmin.tache("backup")).assertDoesNotExist()
        assertEquals("la santé est à l'opérateur seul", false, service.lectures.contains("sante"))
        ecran.onNodeWithTag(ReperesAdmin.surLeSite("maintenance")).performScrollTo().assertIsDisplayed()

        // Ce que l'app ne porte pas : « Sur le site », avec son lien.
        ecran.onNodeWithTag(ReperesOnglets.onglet(OngletsSurLeSite.ID)).toucher()
        ecran.onNodeWithTag(ReperesOnglets.surLeSite("imports")).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun lOperateurNOuvreQueLaMaintenance() {
        ouvrir(operateur)
        ecran.onNodeWithTag(ReperesOnglets.onglet("maintenance")).assertIsSelected()
        ecran.onNodeWithTag(ReperesOnglets.onglet("telemetry")).assertDoesNotExist()
        ecran.waitUntil(DELAI_MS) { service.lectures.contains("sante") }
        ecran.onNodeWithTag(ReperesAdmin.tache("backup")).performScrollTo().assertIsDisplayed()
        ecran.onNodeWithText("NŒUD-B en retard").performScrollTo().assertIsDisplayed()
        ecran.onNodeWithTag(ReperesAdmin.ANOMALIES).performScrollTo().assertIsDisplayed()
        ecran.onNodeWithTag(ReperesAdmin.EXPORT).assertDoesNotExist()
    }

    @Test
    fun laTelemetrieChangeDeVue() {
        ouvrir(administrateur)
        ecran.onNodeWithTag(ReperesOnglets.onglet("telemetry")).toucher()
        // La page bouge à l'arrivée du tableau (filtre, graphe) : on ne touche qu'une fois
        // le graphe affiché, puis jusqu'à voir la pastille retenue, essais bornés (#112).
        ecran.attendreQue("le premier tableau", contexte = vuesDemandees) { existe(ReperesAdmin.GRAPHE) }
        toucherJusquA("la pastille « Mois » retenue", ReperesAdmin.vue(PorteeTelemetrie.Mois)) {
            ecran.onAllNodes(hasTestTag(ReperesAdmin.vue(PorteeTelemetrie.Mois)) and isSelected()).fetchSemanticsNodes().isNotEmpty()
        }
        ecran.attendreQue("la relecture « Mois »", contexte = vuesDemandees) { service.portees.lastOrNull() == PorteeTelemetrie.Mois && existe(ReperesAdmin.GRAPHE) }
        // Le filtre est une bascule : on juge l'écran (activé), pas la relecture, pour ne pas le rebasculer.
        toucherJusquA("le filtre activé", ReperesAdmin.FILTRE) {
            ecran.onAllNodes(hasTestTag(ReperesAdmin.FILTRE) and isOn()).fetchSemanticsNodes().isNotEmpty()
        }
        ecran.attendreQue("la relecture sans le gestionnaire", contexte = vuesDemandees) { service.sans.lastOrNull() == true }
    }

    /** #127 : « Accès : site ou application » — une ligne par canal servi, et l'accès de chaque page. */
    @Test
    fun laTelemetrieDitLAccesSiteOuApplication() {
        ouvrir(administrateur)
        ecran.onNodeWithTag(ReperesOnglets.onglet("telemetry")).toucher()
        ecran.attendreQue("le premier tableau", contexte = vuesDemandees) { existe(ReperesAdmin.GRAPHE) }
        ecran.amenerTag(ReperesAdmin.acces("android")).assert(hasText("36 vues ·\u00A025 %", substring = true))
        ecran.amenerTag(ReperesAdmin.acces("web")).assert(hasText("18 utilisateurs", substring = true))
        ecran.attendreTexte("App Android\u00A018")
    }

    @Test
    fun lesUtilisateursEtLaFiche() {
        ouvrir(administrateur)
        ecran.onNodeWithTag(ReperesOnglets.onglet("utilisateurs")).toucher()
        ecran.waitUntil(DELAI_MS) { service.lectures.contains("utilisateurs") }
        ecran.onNodeWithTag(ReperesAdmin.utilisateur(11)).assertExists()
        ecran.onNodeWithTag(ReperesAdmin.RECHERCHE).performTextInput("dominique")
        ecran.onNodeWithTag(ReperesAdmin.utilisateur(11)).assertDoesNotExist()
        ecran.onNodeWithTag(ReperesAdmin.utilisateur(12)).toucher()
        ecran.onNodeWithTag(ReperesAdmin.FICHE_FERMER).assertIsDisplayed()
        ecran.onNodeWithTag(ReperesAdmin.FICHE_SUPPRIMER).assertExists()
        ecran.onNodeWithTag(ReperesAdmin.role("operateur")).assertDoesNotExist()
        // Un geste ferme la fiche, son annonce suit.
        ecran.onNodeWithTag(ReperesAdmin.FICHE_COMMUNAUTE).toucher()
        ecran.waitUntil(DELAI_MS) { service.lectures.contains("ban-12") }
        ecran.waitUntil(DELAI_MS) { ecran.onAllNodesWithTag(ReperesAdmin.FICHE_FERMER).fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun laConfirmationDitVotreCompte() {
        ouvrir(administrateur)
        ecran.onNodeWithTag(ReperesOnglets.onglet("utilisateurs")).toucher()
        ecran.waitUntil(DELAI_MS) { service.lectures.contains("utilisateurs") }
        ecran.onNodeWithTag(ReperesAdmin.utilisateur(11)).toucher()
        ecran.onNodeWithTag(ReperesAdmin.role("conseil_syndical")).toucher()
        ecran.onNodeWithText("Retirer le rôle Conseil syndical de votre compte ?").assertIsDisplayed()
        ecran.onNodeWithTag(ReperesAdmin.FICHE_CONFIRMER).toucher()
        ecran.waitUntil(DELAI_MS) { service.lectures.contains("role-11-conseil_syndical") }
    }

    @Test
    fun lesDemandesDeProfilSeTraitent() {
        ouvrir(administrateur)
        ecran.onNodeWithTag(ReperesOnglets.onglet("a_traiter")).assertIsSelected()
        ecran.waitUntil(DELAI_MS) { service.lectures.contains("demandes") }
        ecran.onNodeWithTag(ReperesAdmin.demande(41)).performScrollTo().assertIsDisplayed()
        ecran.onNodeWithTag("${ReperesAdmin.demande(41)}.approuver").toucher()
        ecran.waitUntil(DELAI_MS) { ecran.onAllNodesWithTag(ReperesAdmin.demande(41)).fetchSemanticsNodes().isEmpty() }
        ecran.onNodeWithTag("${ReperesAdmin.demande(42)}.rejeter").toucher()
        ecran.onNodeWithTag(ReperesAdmin.DEMANDE_MOTIF).performTextInput("Bail non fourni")
        ecran.onNodeWithTag(ReperesAdmin.DEMANDE_REJETER).toucher()
        ecran.waitUntil(DELAI_MS) { service.lectures.contains("rejeter-42-Bail non fourni") }
    }

    private fun existe(repere: String) = ecran.onAllNodesWithTag(repere).fetchSemanticsNodes().isNotEmpty()

    /** Toucher ce repère jusqu'à voir `vu`, au plus `ESSAIS` fois : un toucher tombé à côté d'une page qui bougeait se rejoue. */
    private fun toucherJusquA(quoi: String, repere: String, vu: () -> Boolean) {
        repeat(ESSAIS) {
            if (vu()) return
            ecran.onNodeWithTag(repere).toucher()
            ecran.waitForIdle()
            if (runCatching { ecran.waitUntil(DELAI_MS / ESSAIS, vu) }.isSuccess) return
        }
        ecran.attendreQue(quoi, contexte = vuesDemandees, condition = vu)
    }

    /** Ce qu'un échec d'attente dit en plus de l'écran : les vues demandées au service. */
    private val vuesDemandees = { "vues demandées ${service.portees}" }

    private companion object {
        /** L'émulateur de la CI est lent : le délai des autres tests d'interface. */
        const val DELAI_MS = 10_000L

        /** Les touchers rejoués au plus, quand la page bougeait sous le doigt. */
        const val ESSAIS = 3
    }

    /** La résidence simulée : des réponses inventées, aucun nom ni machine réels. Lue par le fil du test, écrite par celui de l'écran. */
    private class ServiceSimule : ServiceAdmin {
        val lectures: MutableList<String> = CopyOnWriteArrayList()
        val portees: MutableList<PorteeTelemetrie> = CopyOnWriteArrayList()
        val sans: MutableList<Boolean> = CopyOnWriteArrayList()

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
                listOf(
                    TableauTelemetrie.PageVue(
                        "/tableau-de-bord", 58, 21,
                        listOf(TableauTelemetrie.VuesCanal("web", "Site web", 40), TableauTelemetrie.VuesCanal("android", "App Android", 18)),
                    ),
                ),
                listOf(TableauTelemetrie.UtilisateurActif("Camille EXEMPLE", "locataire", 34, 9, null)),
                TableauTelemetrie.FiltreGestionnaire(true, true, sansGestionnaire, null),
                listOf(
                    TableauTelemetrie.PartCanal("web", "Site web", 110, 75, 18),
                    TableauTelemetrie.PartCanal("android", "App Android", 36, 25, 6),
                ),
            )
        }

        override suspend fun utilisateurs(): List<UtilisateurAdmin> {
            lectures += "utilisateurs"
            return listOf(
                UtilisateurAdmin(
                    11, "Camille", "Exemple", "camille@exemple.test", "copropriétaire_résident",
                    listOf("conseil_syndical", "admin"), actif = true, emailVerifie = true, aDesLots = true,
                    gestesRoles = listOf(GesteDeRole("conseil_syndical", GesteRole.Retirer)), nomServi = "Camille EXEMPLE",
                ),
                UtilisateurAdmin(
                    12, "Dominique", "Fictif", "dominique@exemple.test", "locataire", listOf("résident"),
                    actif = true, emailVerifie = true, aDesLots = true, gestesRoles = listOf(GesteDeRole("conseil_syndical", GesteRole.Ajouter)),
                    nomServi = "Dominique FICTIF",
                ),
            )
        }

        override suspend fun lecteur() = Lecteur(11, instanceCommune = false)

        override suspend fun changerRole(id: Int, geste: GesteDeRole) {
            lectures += "role-$id-${geste.role}"
        }

        override suspend fun bannirCommunaute(id: Int, interdit: Boolean): BanCommunaute {
            lectures += "ban-$id"
            return BanCommunaute(banni = interdit, jusquAu = Instant.parse("2026-12-15T10:00:00Z"))
        }

        override suspend fun relancerRapprochement(id: Int) = PhraseAvis("Aucun lot trouvé.", TonAvis.Avertissement)

        override suspend fun supprimerUtilisateur(id: Int) {
            lectures += "supprimer-$id"
        }

        private var demandes = listOf(
            DemandeProfil(41, "Sacha EXEMPLE", "sacha@exemple.test", "copropriétaire_résident", "Bâtiment A", statutSouhaite = "copropriétaire_bailleur"),
            DemandeProfil(42, "Morgan FICTIF", batimentNomSouhaite = "Bâtiment B", creeLe = Instant.parse("2026-11-08T17:10:00Z")),
        )

        override suspend fun demandesProfil(): List<DemandeProfil> {
            lectures += "demandes"
            return demandes
        }

        override suspend fun traiterDemandeProfil(id: Int, approuver: Boolean, motif: String?): PhraseAvis {
            lectures += if (approuver) "approuver-$id" else "rejeter-$id-$motif"
            demandes = demandes.filterNot { it.id == id }
            return PhraseAvis(if (approuver) "Demande approuvée." else "Demande rejetée.")
        }
    }
}
