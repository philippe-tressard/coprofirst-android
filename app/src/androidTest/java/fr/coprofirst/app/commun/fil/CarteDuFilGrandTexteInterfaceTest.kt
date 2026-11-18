// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.fil

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.coprofirst.app.ThemeCoproFirst
import fr.coprofirst.app.charte.Mise
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * #295, le témoin de l'iOS (`CarteDuFilGrandTexteTests`) : au plus grand texte et sur une largeur
 * de téléphone (375 dp), un long mot de titre ne tenait pas dans la colonne que la carte EN LIGNE
 * laissait au texte (gouttière, pièces, chevron à côté) ; il tient sur toute la largeur de la
 * carte EMPILÉE. Les mesures sont celles de `CarteDuFil` et de `Carte`.
 */
@RunWith(AndroidJUnit4::class)
class CarteDuFilGrandTexteInterfaceTest {
    @get:Rule
    val ecran = createComposeRule()

    @Test
    fun unLongMotTientDansLaCarteEmpileeEtPasEnLigne() {
        var mot = 0f
        var enLigne = 0f
        var empile = 0f
        ecran.setContent {
            val densite = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(densite.density, fontScale = PLUS_GRAND_TEXTE)) {
                ThemeCoproFirst {
                    val mesure = rememberTextMeasurer()
                    val d = LocalDensity.current
                    mot = with(d) { mesure.measure(MOT, MaterialTheme.typography.titleMedium).size.width.toDp().value }
                    // La carte : 16 dp de marge de chaque côté, le liseré d'une urgence (4 dp).
                    val texte = LARGEUR - 2 * 16f - 4f
                    // En ligne : la gouttière (36), les pièces (Mise.vignette), le chevron (24), trois écarts (12).
                    enLigne = texte - 36f - Mise.vignette.value - 24f - 3 * 12f
                    empile = texte
                }
            }
        }
        ecran.waitForIdle()
        assertTrue("le témoin : « $MOT » ($mot dp) ne tenait pas en ligne ($enLigne dp)", mot > enLigne)
        assertTrue("« $MOT » ($mot dp) tient dans la carte empilée ($empile dp)", empilee(PLUS_GRAND_TEXTE) && mot <= empile)
    }

    private companion object {
        const val PLUS_GRAND_TEXTE = 2f
        const val LARGEUR = 375f
        const val MOT = "Imperméabilisation"
    }
}
