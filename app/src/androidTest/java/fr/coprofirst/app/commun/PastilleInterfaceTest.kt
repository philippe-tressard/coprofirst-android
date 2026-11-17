// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun

import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.ThemeCoproFirst
import fr.coprofirst.app.charte.Couleurs
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * #259 : une pastille retenue prend le fond plein de la charte, ou la teinte pleine d'un ÉTAT
 * qu'on lui donne (`pleine(badge)`, l'état d'un avis, comme le `BoutonEtat` de l'iOS).
 */
@RunWith(AndroidJUnit4::class)
class PastilleInterfaceTest {
    @get:Rule
    val ecran = createComposeRule()

    @Test
    fun laRetenuePrendLaTeintePleineDeSonEtat() {
        lateinit var couleurs: Couleurs
        var bord = 0
        ecran.setContent {
            ThemeCoproFirst {
                couleurs = LocalCouleurs.current
                // Un point du fond, hors du libellé : dans l'arrondi de gauche, avant la marge du texte.
                bord = with(LocalDensity.current) { 6.dp.roundToPx() }
                Row {
                    Pastille("Filtre", retenue = true, modifier = Modifier.testTag("charte")) {}
                    Pastille("Mauvais", retenue = true, modifier = Modifier.testTag("etat"), teinte = pleine("badge-red", couleurs)) {}
                }
            }
        }
        assertEquals(couleurs.primaryPlein, fond("charte", bord))
        assertEquals(couleurs.dangerPlein, fond("etat", bord))
        ecran.onNodeWithTag("etat").assertIsSelected()
    }

    @Test
    fun chaqueClasseADeuxTeintesPleinesConnues() {
        lateinit var couleurs: Couleurs
        ecran.setContent { ThemeCoproFirst { couleurs = LocalCouleurs.current } }
        ecran.waitForIdle()
        assertEquals(couleurs.successPlein, pleine("badge-green", couleurs))
        assertEquals(couleurs.textMuted, pleine("badge-gray", couleurs))
    }

    private fun fond(repere: String, x: Int): Color {
        val image = ecran.onNodeWithTag(repere).captureToImage().toPixelMap()
        return image[x, image.height / 2]
    }
}
