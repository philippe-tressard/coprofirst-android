// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
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
}
