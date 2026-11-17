// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun

import android.view.View
import android.view.WindowInsets
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Le geste commun lui-même (#119) : il amène l'élément à l'écran, où qu'il soit, avant de le toucher. */
@RunWith(AndroidJUnit4::class)
class GestesInterfaceTest {
    @get:Rule
    val ecran = createComposeRule()

    /**
     * Une pastille au bout d'une rangée horizontale, elle-même sous le pli d'une page verticale
     * (le A5 de la nouvelle annonce du hall, #112) : la page défile jusqu'à la rangée, puis la
     * rangée jusqu'à la pastille — `performScrollTo` seul ne faisait que la rangée.
     */
    @Test
    fun unePastilleDUneRangeeSousLePliSeTouche() {
        var touchee = -1
        ecran.setContent {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Spacer(Modifier.height(3000.dp))
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    repeat(30) { i ->
                        Text("Pastille $i", Modifier.padding(16.dp).testTag("pastille.$i").clickable { touchee = i })
                    }
                }
            }
        }
        ecran.toucherTag("pastille.29")
        ecran.waitForIdle()
        assertEquals(29, touchee)
    }

    /**
     * #255 : juste après une frappe, le bouton du pied de page est sous le clavier, et la page
     * rétrécie par lui (`imePadding`). Le geste ferme le clavier tel que L'APP le voit (son encart
     * `ime`), et pas seulement quand l'accessibilité liste sa fenêtre : au moment du toucher,
     * l'encart est nul.
     */
    @Test
    fun leBoutonSousLeClavierSeToucheClavierFerme() {
        var encartAuToucher = -1
        lateinit var vue: View
        ecran.setContent {
            vue = LocalView.current
            var texte by remember { mutableStateOf("") }
            Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())) {
                TextField(texte, { texte = it }, Modifier.testTag("champ"))
                Spacer(Modifier.height(900.dp))
                Text(
                    "Envoyer",
                    Modifier.padding(16.dp).testTag("envoyer").clickable {
                        encartAuToucher = vue.rootWindowInsets.getInsets(WindowInsets.Type.ime()).bottom
                    },
                )
            }
        }
        ecran.onNodeWithTag("champ").performTextInput("Interphone muet")
        // Le clavier est ouvert pour l'app (son encart), comme à la CI au moment de l'échec.
        ecran.waitUntil(DELAI_GESTE_MS) { vue.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) == true }
        ecran.toucherTag("envoyer")
        ecran.waitForIdle()
        assertEquals("touché clavier fermé", 0, encartAuToucher)
    }
}
