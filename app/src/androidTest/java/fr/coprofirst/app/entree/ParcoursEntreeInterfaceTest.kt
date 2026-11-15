// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.entree

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Le parcours d'entrée à l'écran, contre un serveur local : ce qu'un utilisateur voit. */
@RunWith(AndroidJUnit4::class)
class ParcoursEntreeInterfaceTest {
    @get:Rule
    val ecran = createComposeRule()
    private val serveur = ServeurDeTest()

    @Before
    fun ouvrir() {
        val parcours = serveur.parcours()
        ecran.setContent { NavigationEntree(serveur.apparence(), parcours) }
        ecran.onNodeWithText("Entrer dans ma résidence").performClick()
    }

    @After
    fun fermer() = serveur.close()

    private fun attendre(texte: String) = ecran.waitUntil(DELAI_MS) {
        ecran.onAllNodes(hasText(texte, substring = true)).fetchSemanticsNodes().isNotEmpty()
    }

    /** Le défaut trouvé à l'émulateur pendant #3 : après un échec, le champ perdait le focus. */
    @Test
    fun apresUnEchecLaSaisieReprendDansLeChamp() {
        serveur.repondre(421)
        val champ = ecran.onNodeWithTag(Reperes.RESIDENCE)
        champ.performTextInput("inconnue.exemple.test")
        attendre("aucune résidence n'y est installée")
        champ.assertIsFocused()
        serveur.residence("Les Glycines")
        champ.performTextClearance()
        champ.performTextInput("residence.exemple.test")
        attendre("Les Glycines")
        champ.assertIsFocused()
    }

    /** Un hôte jamais vu se confirme avant tout mot de passe, puis la session s'ouvre et se ferme. */
    @Test
    fun hoteJamaisVuConfirmeAvantLeMotDePasse() {
        serveur.residence("Les Glycines")
        ecran.onNodeWithTag(Reperes.RESIDENCE).performTextInput("jamais-vu.exemple.test")
        attendre("Les Glycines")
        ecran.onNodeWithTag(Reperes.EMAIL).performTextInput("a@exemple.test")
        ecran.onNodeWithTag(Reperes.MOT_DE_PASSE).performTextInput("secret")
        ecran.onNodeWithTag(Reperes.VALIDER).assertIsEnabled().performClick()
        attendre("Vérifiez l'adresse")
        attendre("Adresse reconnue comme une installation CoproFirst.")
        assertEquals("seul GET /config est parti", 1, serveur.serveur.requestCount)
        serveur.repondre(200) // POST /auth/login
        serveur.repondre(404) // GET /auth/me : sans profil, la barre reste celle de tous
        ecran.onNodeWithText("Me connecter à cette adresse").performClick()
        // La résidence ouverte : la barre, la première page (pas encore portée : « Bientôt »).
        attendre("Bientôt")
        ecran.onNodeWithText("Plus").performClick()
        // « Se déconnecter » est au bas du Profil, comme sur l'iOS (#21) — là quel que soit
        // l'état du compte (chargé, en erreur, en chargement) — et se confirme.
        serveur.repondre(404) // GET /auth/me du Profil
        serveur.repondre(200) // POST /auth/logout
        ecran.onNodeWithTag("plus.profil").performClick()
        ecran.waitUntil(DELAI_MS) { ecran.onAllNodes(hasTestTag("profil.deconnecter")).fetchSemanticsNodes().isNotEmpty() }
        ecran.onNodeWithTag("profil.deconnecter").performClick()
        ecran.onNodeWithTag("sortie.confirmer").performClick()
        attendre("Connectez-vous à votre espace")
    }

    private companion object {
        const val DELAI_MS = 10_000L
    }
}
