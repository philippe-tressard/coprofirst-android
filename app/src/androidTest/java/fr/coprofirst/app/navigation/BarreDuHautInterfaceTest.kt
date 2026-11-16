// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.coprofirst.app.ThemeCoproFirst
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** L'action d'un écran paraît dans la barre du haut tant qu'il est affiché, et s'en va avec lui. */
@RunWith(AndroidJUnit4::class)
class BarreDuHautInterfaceTest {
    @get:Rule
    val ecran = createComposeRule()

    @Test
    fun lActionSuitLEcranAffiche() {
        val barre = BarreDuHaut()
        var affiche by mutableStateOf(true)
        ecran.setContent {
            ThemeCoproFirst {
                Column {
                    Row { ActionsDeclarees(barre) }
                    if (affiche) ActionsDeBarre(barre) { Text("Nouvel accès") }
                }
            }
        }
        ecran.onNodeWithText("Nouvel accès").assertIsDisplayed()
        affiche = false
        ecran.waitForIdle()
        ecran.onNodeWithText("Nouvel accès").assertDoesNotExist()
    }
}
