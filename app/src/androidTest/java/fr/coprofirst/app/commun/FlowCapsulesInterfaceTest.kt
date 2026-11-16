// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.coprofirst.app.ThemeCoproFirst
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * #61, pendant de coprofirst-ios#202 : dans une rangée de pastilles (`FlowCapsules`), une
 * pastille plus large que la rangée prend une ligne à elle et passe à la ligne DANS la largeur
 * de la carte — elle n'en déborde pas, au plus grand texte compris.
 */
@RunWith(AndroidJUnit4::class)
class FlowCapsulesInterfaceTest {
    @get:Rule
    val ecran = createComposeRule()

    @Test
    fun unePastillePlusLargeQueSaRangeePasseALaLigneDansLaCarte() {
        ecran.setContent {
            val densite = LocalDensity.current
            // Le plus grand texte d'Android (200 %).
            CompositionLocalProvider(LocalDensity provides Density(densite.density, fontScale = 2f)) {
                ThemeCoproFirst {
                    Box(Modifier.width(LARGEUR).testTag(CARTE)) {
                        Carte {
                            FlowCapsules(Modifier.testTag(RANGEE)) {
                                PastilleTeinte("🔖 C-2026-001", "badge-gray", Modifier.testTag(AVANT), compacte = true)
                                PastilleTeinte(
                                    "🔁 Remplacé par le contrat d'entretien des ascenseurs du Parc à partir du 1er janvier 2027",
                                    "badge-gray", Modifier.testTag(LONGUE), compacte = true,
                                )
                                PastilleTeinte("↺ Mensuel", "badge-gray", Modifier.testTag(APRES), compacte = true)
                            }
                        }
                    }
                }
            }
        }
        val rangee = ecran.onNodeWithTag(RANGEE).getBoundsInRoot()
        val longue = ecran.onNodeWithTag(LONGUE).getBoundsInRoot()
        val avant = ecran.onNodeWithTag(AVANT).getBoundsInRoot()
        val apres = ecran.onNodeWithTag(APRES).getBoundsInRoot()
        assertTrue("la longue ne dépasse pas le bord droit de la rangée : $longue / $rangee", longue.right <= rangee.right)
        assertTrue("ni le gauche", longue.left >= rangee.left)
        assertTrue("elle prend une ligne à elle, sous la précédente", longue.top >= avant.bottom)
        assertTrue("la suivante passe dessous", apres.top >= longue.bottom)
        assertTrue("elle s'est étalée sur plusieurs lignes, pas tronquée à une : ${longue.bottom - longue.top}", (longue.bottom - longue.top) > (avant.bottom - avant.top) * 1.5f)
    }

    private companion object {
        val LARGEUR = 240.dp
        const val CARTE = "carte"
        const val RANGEE = "rangee"
        const val AVANT = "avant"
        const val LONGUE = "longue"
        const val APRES = "apres"
    }
}
