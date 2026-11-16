// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.navigation

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.coprofirst.app.ThemeCoproFirst
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Le point « non lues » de la cloche, comme `ClocheNotifications` de l'iOS : il suit le nombre
 * noté pour SA résidence (`CompteurNonLues`), et TalkBack dit ce nombre.
 */
@RunWith(AndroidJUnit4::class)
class ClocheInterfaceTest {
    @get:Rule
    val ecran = createComposeRule()

    private val page = PageDeNavigation(Cloche.ID_PAGE, "/notifications", "Notifications", "Notifications", "bell")

    private fun etat(phrase: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, phrase)

    private fun point(present: Boolean) = ecran.waitUntil(DELAI_MS) {
        ecran.onAllNodes(hasTestTag(Cloche.REPERE_POINT), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() == present
    }

    @Test
    fun lePointSuitLeNombreDeSaResidence() {
        val compteur = CompteurNonLues()
        val residence = "https://glycines.test"
        ecran.setContent {
            ThemeCoproFirst { BoutonCloche(page, compteur.nombre(residence)) {} }
        }
        ecran.onNodeWithTag(Cloche.REPERE).assertIsDisplayed()
        point(present = false)

        compteur.noter(residence, 12)
        point(present = true)
        ecran.onNodeWithTag(Cloche.REPERE).assert(etat("12 non lues"))

        compteur.noter("https://tilleuls.test", 0)
        ecran.waitForIdle()
        ecran.onNodeWithTag(Cloche.REPERE).assert(etat("12 non lues"))

        compteur.noter(residence, 0)
        point(present = false)
    }

    private companion object {
        const val DELAI_MS = 10_000L
    }
}
