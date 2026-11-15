// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.profil

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.coprofirst.app.entree.NavigationEntree
import fr.coprofirst.app.entree.Reperes
import fr.coprofirst.app.entree.ServeurDeTest
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
 * Plus › Profil, comme l'iOS (`SuppressionCompteUITests`) : la demande de suppression du
 * compte depuis l'app — ce qui sera effacé dit d'abord, le mot de passe exigé, la phrase
 * du serveur sur un refus, la demande retirable. Contre un serveur local en HTTPS, qui
 * refuse le mot de passe « refuse » comme le ferait le vrai.
 */
@RunWith(AndroidJUnit4::class)
class ProfilInterfaceTest {
    @get:Rule
    val ecran = createComposeRule()
    private val serveur = ServeurDeTest()

    /** La demande en cours, côté serveur. */
    @Volatile
    private var demandeOuverte = false
    private val motsDePasseRecus = mutableListOf<String>()

    private val residence = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val chemin = request.url.encodedPath
            return when {
                chemin.endsWith("/config") -> json(200, """{"site_nom": "Les Glycines"}""")
                chemin.endsWith("/auth/login") || chemin.endsWith("/auth/logout") -> json(200, "{}")
                chemin.endsWith("/auth/me") -> json(200, FixturesProfil.MOI)
                chemin.endsWith("/auth/demande-suppression") -> suppression(request)
                else -> json(404, """{"detail": "Introuvable."}""")
            }
        }
    }

    private fun suppression(request: RecordedRequest): MockResponse = when (request.method) {
        "POST" -> {
            val corps = request.body?.utf8().orEmpty()
            synchronized(motsDePasseRecus) { motsDePasseRecus += corps }
            if ("\"refuse\"" in corps) {
                json(400, FixturesProfil.MOT_DE_PASSE_INCORRECT)
            } else {
                demandeOuverte = true
                json(201, FixturesProfil.DEMANDE)
            }
        }
        "DELETE" -> {
            demandeOuverte = false
            MockResponse.Builder().code(204).build()
        }
        else -> json(200, if (demandeOuverte) FixturesProfil.ETAT_AVEC_DEMANDE else FixturesProfil.ETAT_SANS_DEMANDE)
    }

    private fun json(code: Int, corps: String) =
        MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(corps).build()

    @Before
    fun ouvrirLeProfil() {
        serveur.serveur.dispatcher = residence
        val parcours = serveur.parcours()
        ecran.setContent { NavigationEntree(parcours) }
        ecran.onNodeWithText("Entrer dans ma résidence").performClick()
        ecran.onNodeWithTag(Reperes.RESIDENCE).performTextInput("residence.exemple.fr")
        attendre("Les Glycines")
        ecran.onNodeWithTag(Reperes.EMAIL).performTextInput("camille@exemple.fr")
        ecran.onNodeWithTag(Reperes.MOT_DE_PASSE).performTextInput("secret")
        ecran.onNodeWithTag(Reperes.VALIDER).performClick()
        attendre("Me connecter à cette adresse")
        ecran.onNodeWithText("Me connecter à cette adresse").performClick()
        attendre("Plus")
        ecran.onNodeWithText("Plus").performClick()
        ecran.onNodeWithTag("plus.profil").performClick()
        attendre("Camille EXEMPLE")
    }

    @After
    fun fermer() = serveur.close()

    private fun attendre(texte: String) = ecran.waitUntil(DELAI_MS) {
        ecran.onAllNodes(hasText(texte, substring = true)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun attendreRepere(repere: String) = ecran.waitUntil(DELAI_MS) {
        ecran.onAllNodes(hasTestTag(repere)).fetchSemanticsNodes().isNotEmpty()
    }

    /** Amène un repère de la liste du Profil à l'écran (elle se compose à mesure). */
    private fun amener(repere: String): SemanticsNodeInteraction {
        ecran.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasTestTag(repere))
        attendreRepere(repere)
        return ecran.onNodeWithTag(repere)
    }

    @Test
    fun leProfilDitLaResidenceEtLeStatutServis() {
        attendre("Bât. A, T3, 2ème")
        attendre("Copropriétaire résident")
        attendre("Conseil syndical")
    }

    @Test
    fun laDemandeSeFaitAvecLeMotDePasseEtSeRetire() {
        amener("profil.suppression.demander").performClick()

        // La feuille dit ce qui sera effacé (phrases servies) avant de demander quoi que ce soit.
        attendre("Ancien résident")
        ecran.onNodeWithTag("suppression.confirmer").assertIsNotEnabled()

        // Un mot de passe refusé par le serveur : sa phrase, la feuille reste.
        val motDePasse = ecran.onNodeWithTag("suppression.motDePasse")
        motDePasse.performTextInput("refuse")
        ecran.onNodeWithTag("suppression.confirmer").performClick()
        attendre("Mot de passe incorrect.")

        // Le bon mot de passe : la demande part, la feuille se ferme, on peut la retirer.
        motDePasse.performTextClearance()
        motDePasse.performTextInput("secret")
        ecran.onNodeWithTag("suppression.confirmer").performClick()
        attendre("Un accusé de réception vous est adressé.")
        amener("profil.suppression.retirer")
        assertTrue(ecran.onAllNodes(hasTestTag("profil.suppression.demander")).fetchSemanticsNodes().isEmpty())
        assertTrue("le mot de passe est parti, au serveur local seul", motsDePasseRecus.size == 2)

        // Retirée : le compte est conservé, le geste revient.
        ecran.onNodeWithTag("profil.suppression.retirer").performClick()
        attendre("votre compte est conservé")
        amener("profil.suppression.demander")
    }

    private companion object {
        const val DELAI_MS = 10_000L
    }
}
