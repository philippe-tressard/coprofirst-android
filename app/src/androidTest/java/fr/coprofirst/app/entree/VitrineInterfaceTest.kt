// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.entree

import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.coprofirst.app.commun.amenerTag
import fr.coprofirst.app.commun.attendreTexte
import fr.coprofirst.app.commun.toucherTag
import fr.coprofirst.app.commun.toucherTexte
import fr.coprofirst.app.entree.vitrine.REPERE_APERCU_FIL
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** #10 : la vitrine et la démo, de l'accueil à la connexion d'un rôle — ce qu'un visiteur voit. */
@RunWith(AndroidJUnit4::class)
class VitrineInterfaceTest {
    @get:Rule
    val ecran = createComposeRule()
    private val serveur = ServeurDeTest()

    @Before
    fun ouvrir() {
        val parcours = serveur.parcours()
        ecran.setContent { NavigationEntree(serveur.apparence(), parcours) }
    }

    @After
    fun fermer() = serveur.close()

    @Test
    fun deLaVitrineALaConnexionDUnRoleDeLaDemo() {
        ecran.toucherTexte("En savoir plus sur CoproFirst")
        ecran.onNodeWithTag("vitrine.titre").assertIsDisplayed()
        // #153 : le fil en miniature, rendu par l'app, lu d'un bloc et sans toucher.
        ecran.amenerTag(REPERE_APERCU_FIL).assertContentDescriptionEquals("Aperçu du fil d'une résidence").assertHasNoClickAction()
        // Les six fonctions de l'app, textes du site.
        ecran.amenerTag("vitrine.rangee.communaute")
        ecran.toucherTexte("Essayer la démo")
        ecran.attendreTexte("Essayez-la sans rien installer")
        serveur.residence("Résidence de démonstration")
        ecran.toucherTag("demo.user-cs")
        ecran.attendreTexte("Le mot de passe de « Conseil syndical » est publié sur coprofirst.fr.")
        ecran.attendreTexte("user-cs@demo.coprofirst.fr")
        ecran.attendreTexte("Résidence de démonstration")
    }
}
