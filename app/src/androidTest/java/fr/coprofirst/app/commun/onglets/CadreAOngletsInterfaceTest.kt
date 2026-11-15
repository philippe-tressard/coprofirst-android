// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.onglets

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.coprofirst.app.R
import fr.coprofirst.app.ThemeCoproFirst
import fr.coprofirst.app.commun.Apparence
import fr.coprofirst.app.commun.GestesDeSortie
import fr.coprofirst.app.commun.StockageApparence
import fr.coprofirst.app.navigation.BarreDuHaut
import fr.coprofirst.app.navigation.ContexteDeMenu
import fr.coprofirst.app.navigation.DroitsOnglet
import fr.coprofirst.app.navigation.OngletsDuSite
import fr.coprofirst.app.navigation.PagesParDefaut
import fr.coprofirst.app.navigation.ouverts
import fr.coprofirst.app.session.Installation
import fr.coprofirst.app.session.SessionOuverte
import fr.coprofirst.app.session.SessionsParHote
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Le cadre à onglets : l'onglet touché s'affiche, un onglet non porté dit « Bientôt », une
 * page réservée le dit. Repères de l'iOS : `onglet.<id>`.
 */
@RunWith(AndroidJUnit4::class)
class CadreAOngletsInterfaceTest {
    @get:Rule
    val ecran = createComposeRule()

    private val conseil = DroitsOnglet.depuis(listOf("conseil_syndical"), null, "copropriétaire_résident", estModerateur = true)

    private val page = PageAOnglets(
        "espace-cs",
        mapOf(
            "validations" to { contexte -> Text("Écran des comptes (${contexte.onglet.libelle})") },
            "badges" to { _ -> Text("Écran des badges") },
        ),
        R.string.onglets_sur_le_site,
    )

    private fun contexte(droits: DroitsOnglet) = ContexteDeMenu(
        page = PagesParDefaut.toutes.first { it.id == "espace-cs" },
        session = SessionOuverte(Installation("https://residence.example.test", "Résidence"), SessionsParHote()),
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

    @Test
    fun lOngletToucheSAffiche() {
        val onglets = OngletsDuSite.ouverts("espace-cs", conseil)
        ecran.setContent { ThemeCoproFirst { CadreAOnglets(contexte(conseil), page) } }

        ecran.onNodeWithTag(ReperesOnglets.onglet("validations")).assertIsSelected()
        ecran.onNodeWithText("Écran des comptes", substring = true).assertIsDisplayed()

        ecran.onNodeWithTag(ReperesOnglets.onglet("badges")).performClick()
        ecran.waitForIdle()
        ecran.onNodeWithTag(ReperesOnglets.onglet("badges")).assertIsSelected()
        ecran.onNodeWithText("Écran des badges").assertIsDisplayed()
        ecran.onNodeWithText("Écran des comptes", substring = true).assertDoesNotExist()

        // Un onglet du site que l'app ne porte pas : « Bientôt ».
        val absent = onglets.first { it.id !in page.ecrans.keys }
        ecran.onNodeWithTag(ReperesOnglets.onglet(absent.id)).performScrollTo().performClick()
        ecran.waitForIdle()
        ecran.onNodeWithText("Bientôt").assertIsDisplayed()
        ecran.onNodeWithText("Écran des badges").assertDoesNotExist()
    }

    @Test
    fun unePageReserveeLeDit() {
        val resident = DroitsOnglet.depuis(listOf("résident"), null, "locataire", estModerateur = false)
        ecran.setContent { ThemeCoproFirst { CadreAOnglets(contexte(resident), page) } }

        ecran.onNodeWithTag(ReperesOnglets.RESERVEE).assertIsDisplayed()
        ecran.onNodeWithTag(ReperesOnglets.onglet("validations")).assertDoesNotExist()
        ecran.onNodeWithText("Écran des comptes", substring = true).assertDoesNotExist()
    }
}
