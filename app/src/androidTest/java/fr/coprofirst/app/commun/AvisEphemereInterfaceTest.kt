// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.coprofirst.app.ThemeCoproFirst
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** L'avis éphémère paraît au geste, reste le temps du site, puis s'en va ; le suivant le remplace. */
@RunWith(AndroidJUnit4::class)
class AvisEphemereInterfaceTest {
    @get:Rule
    val ecran = createComposeRule()

    @Test
    fun lAvisParaitPuisSEnVa() {
        val avis = AvisDuGeste()
        ecran.setContent { ThemeCoproFirst { AvisEphemere(avis) { Text("La page") } } }
        ecran.onNodeWithTag(REPERE_AVIS).assertDoesNotExist()

        ecran.runOnIdle { avis.dire("Accès enregistré") }
        ecran.onNodeWithText("Accès enregistré").assertIsDisplayed()

        // Remplacé avant la fin : le suivant reste, seul.
        ecran.mainClock.advanceTimeBy(AvisDuSite.DUREE_MS / 2)
        ecran.runOnIdle { avis.dire("Demande envoyée", TonAvis.Info) }
        ecran.mainClock.advanceTimeBy(AvisDuSite.DUREE_MS / 2 + 500)
        ecran.onNodeWithText("Demande envoyée").assertIsDisplayed()
        ecran.onNodeWithText("Accès enregistré").assertDoesNotExist()

        ecran.mainClock.advanceTimeBy(AvisDuSite.DUREE_MS)
        ecran.waitUntil(timeoutMillis = AvisDuSite.DUREE_MS + 5_000) {
            ecran.onAllNodesWithTag(REPERE_AVIS).fetchSemanticsNodes().isEmpty()
        }
        ecran.onNodeWithText("La page").assertIsDisplayed()
    }
}
