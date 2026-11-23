// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.navigation

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.coprofirst.app.commun.LocalLogos
import fr.coprofirst.app.commun.LogosEnMemoire
import fr.coprofirst.app.commun.REPERE_LOGO_MARQUE
import fr.coprofirst.app.commun.REPERE_LOGO_SERVI
import fr.coprofirst.app.commun.attendre
import fr.coprofirst.app.commun.attendreTexte
import fr.coprofirst.app.commun.residenceSimulee
import fr.coprofirst.app.commun.toucherTag
import fr.coprofirst.app.entree.NavigationEntree
import fr.coprofirst.app.entree.Reperes
import fr.coprofirst.app.entree.ServeurDeTest
import fr.coprofirst.app.entree.entrerDansLaResidence
import fr.coprofirst.app.fonctions.profil.FixturesProfil
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * « Où suis-je » et la feuille « Résidences » (#180), comme l'iOS : la résidence ouverte et ce
 * qu'on y est, les autres avec leurs rôles, et changer sans se déconnecter.
 */
@RunWith(AndroidJUnit4::class)
class ResidencesInterfaceTest {
    @get:Rule
    val ecran = createComposeRule()
    private val serveur = ServeurDeTest()
    private val tilleuls = residenceSimulee("https://tilleuls.exemple.test", "Les Tilleuls")
    private val sorties = mutableListOf<String>()

    /** Les logos demandés (#137) : l'adresse que l'app calcule, servie ici par une image de test. */
    private val logosDemandes = mutableListOf<String>()
    private val logos = LogosEnMemoire { adresse -> synchronized(logosDemandes) { logosDemandes += adresse }; ImageBitmap(8, 8) }

    private val residence = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val chemin = request.url.encodedPath
            return when {
                chemin.endsWith("/config") -> json("""{"site_nom": "Les Glycines", "site_logo": "/uploads/logo.png"}""")
                chemin.endsWith("/auth/logout") -> json("{}").also { synchronized(sorties) { sorties += chemin } }
                chemin.endsWith("/auth/login") -> json("{}")
                chemin.endsWith("/auth/me") -> json(FixturesProfil.MOI)
                chemin.endsWith("/flux") -> json(ServeurDeTest.FIL_VIDE)
                else -> MockResponse.Builder().code(404).addHeader("Content-Type", "application/json").body("""{"detail": "Introuvable."}""").build()
            }
        }
    }

    private fun json(corps: String) = MockResponse.Builder().code(200).addHeader("Content-Type", "application/json").body(corps).build()

    @Before
    fun ouvrir() {
        serveur.serveur.dispatcher = residence
        val connues = serveur.residencesEnMemoire()
        val parcours = serveur.parcours(connues = connues)
        ecran.setContent { CompositionLocalProvider(LocalLogos provides logos) { NavigationEntree(serveur.apparence(), parcours) } }
        ecran.entrerDansLaResidence("residence.exemple.test", "Les Glycines")
        ecran.waitUntil(DELAI_MS) { ecran.onAllNodes(hasTestTag(REPERE_PASTILLE)).fetchSemanticsNodes().isNotEmpty() }
        // Une autre résidence, ouverte un autre jour : connue de l'appareil, avec ses rôles.
        connues.retenir(tilleuls)
        connues.noterRoles(tilleuls, "locataire", emptyList())
    }

    @After
    fun fermer() = serveur.close()

    @Test
    fun laFeuilleDitOuJeSuisEtMesAutresResidences() {
        ecran.toucherTag(REPERE_PASTILLE)
        // Les intitulés de section sont en petites capitales (`Libelle`) : on attend leurs lignes.
        ecran.waitUntil(DELAI_MS) { ecran.onAllNodes(hasTestTag(REPERE_DECONNECTER)).fetchSemanticsNodes().isNotEmpty() }
        ecran.attendreTexte("VOUS ÊTES ICI")
        ecran.attendreTexte("MES AUTRES RÉSIDENCES")
        ecran.onNodeWithTag(repereAutre(tilleuls)).assertIsDisplayed()
        ecran.waitUntil(DELAI_MS) {
            ecran.onAllNodes(hasTestTag(repereAutre(tilleuls)) and hasText("Les Tilleuls", substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
        ecran.onNodeWithTag(REPERE_UNE_AUTRE).assertIsDisplayed()
    }

    /** #137 : la résidence qui sert son logo le montre dans la pastille, au lieu de celui de CoproFirst. */
    @Test
    fun laPastilleMontreLeLogoDeLaResidence() {
        // Le logo est décoratif, dans la pastille qui fusionne ses enfants : on le lit dans l'arbre non fusionné.
        ecran.attendre(hasTestTag(REPERE_LOGO_SERVI), useUnmergedTree = true)
        ecran.onAllNodes(hasTestTag(REPERE_LOGO_MARQUE), useUnmergedTree = true).assertCountEquals(0)
        assertTrue(synchronized(logosDemandes) { logosDemandes.toList() }.toString(), synchronized(logosDemandes) { "https://residence.exemple.test/api/config/logo.png" in logosDemandes })
    }

    /** #181 : le logement servi en tête du fil ; un toucher ouvre « Mes lots & accès ». */
    @Test
    fun leLogementEnTeteDuFilOuvreMesLots() {
        ecran.waitUntil(DELAI_MS) {
            ecran.onAllNodes(hasTestTag("fil.lot") and hasContentDescription("Mon logement : Bât. A, T3, 2ème")).fetchSemanticsNodes().isNotEmpty()
        }
        ecran.toucherTag("fil.lot")
        ecran.waitUntil(DELAI_MS) { ecran.onAllNodes(hasTestTag("fil.lot")).fetchSemanticsNodes().isEmpty() }
    }

    /** Changer de résidence mène à SA connexion, pré-remplie — sans déconnecter celle qu'on quitte. */
    @Test
    fun changerDeResidenceNeDeconnectePas() {
        ecran.toucherTag(REPERE_PASTILLE)
        ecran.toucherTag(repereAutre(tilleuls))
        ecran.waitUntil(DELAI_MS) {
            ecran.onAllNodes(hasTestTag(Reperes.RESIDENCE) and hasText("tilleuls.exemple.test", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue("aucune déconnexion", synchronized(sorties) { sorties.isEmpty() })
    }

    private companion object {
        const val DELAI_MS = 10_000L

    }
}
