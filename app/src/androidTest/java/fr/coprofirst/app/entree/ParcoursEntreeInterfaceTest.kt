// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.entree

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.coprofirst.app.commun.attendreTexte
import fr.coprofirst.app.commun.toucher
import fr.coprofirst.app.commun.toucherTag
import fr.coprofirst.app.commun.toucherTexte
import fr.coprofirst.app.navigation.ouvrirLaPageDePlus
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
        ecran.toucherTexte("Entrer dans ma résidence")
    }

    @After
    fun fermer() = serveur.close()

    /** Le défaut trouvé à l'émulateur pendant #3 : après un échec, le champ perdait le focus. */
    @Test
    fun apresUnEchecLaSaisieReprendDansLeChamp() {
        serveur.repondre(421)
        val champ = ecran.onNodeWithTag(Reperes.RESIDENCE)
        champ.performTextInput("inconnue.exemple.test")
        ecran.attendreTexte("aucune résidence n'y est installée")
        champ.assertIsFocused()
        serveur.residence("Les Glycines")
        champ.performTextClearance()
        champ.performTextInput("residence.exemple.test")
        ecran.attendreTexte("Les Glycines")
        champ.assertIsFocused()
    }

    /**
     * #6 : le QR code de la fiche d'arrivée s'ouvre DANS « Votre résidence », comme l'iOS. La
     * caméra elle-même ne se teste pas à l'émulateur ; la lecture (`LecteurQRTest`) et la
     * garde après un scan (`ParcoursEntreeTest`) se testent sans elle.
     */
    @Test
    fun leQRCodeSOuvreDepuisLeChampResidence() {
        ecran.onNode(hasTestTag(Reperes.SCANNER) and hasContentDescription("Scanner le QR code de la fiche d'arrivée"))
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    /** Un hôte jamais vu se confirme avant tout mot de passe, puis la session s'ouvre et se ferme. */
    @Test
    fun hoteJamaisVuConfirmeAvantLeMotDePasse() {
        serveur.residence("Les Glycines")
        ecran.onNodeWithTag(Reperes.RESIDENCE).performTextInput("jamais-vu.exemple.test")
        ecran.attendreTexte("Les Glycines")
        ecran.onNodeWithTag(Reperes.EMAIL).performTextInput("a@exemple.test")
        ecran.onNodeWithTag(Reperes.MOT_DE_PASSE).performTextInput("secret")
        ecran.onNodeWithTag(Reperes.VALIDER).assertIsEnabled().toucher()
        ecran.attendreTexte("Vérifiez l'adresse")
        ecran.attendreTexte("Adresse reconnue comme une installation CoproFirst.")
        assertEquals("seul GET /config est parti", 1, serveur.serveur.requestCount)
        serveur.repondre(200) // POST /auth/login
        serveur.repondre(404) // GET /auth/me : sans profil, la barre reste celle de tous
        ecran.toucherTexte("Me connecter à cette adresse")
        // La résidence ouverte : la barre, la première page — le fil, vide (`ServeurDeTest.flux`).
        ecran.attendreTexte("Rien de neuf")
        // « Se déconnecter » est au bas du Profil, comme sur l'iOS (#21) — là quel que soit
        // l'état du compte (chargé, en erreur, en chargement) — et se confirme.
        serveur.repondre(404) // GET /auth/me du Profil
        serveur.repondre(200) // POST /auth/logout
        ecran.ouvrirLaPageDePlus("profil")
        ecran.waitUntil(DELAI_MS) { ecran.onAllNodes(hasTestTag("profil.deconnecter")).fetchSemanticsNodes().isNotEmpty() }
        ecran.toucherTag("profil.deconnecter")
        ecran.toucherTag("sortie.confirmer")
        ecran.attendreTexte("Connectez-vous à votre espace")
    }

    private companion object {
        const val DELAI_MS = 10_000L
    }
}
