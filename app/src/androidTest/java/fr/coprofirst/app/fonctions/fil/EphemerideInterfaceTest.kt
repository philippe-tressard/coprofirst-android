// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.fil

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.coprofirst.app.ThemeCoproFirst
import fr.coprofirst.app.charte.Palettes
import fr.coprofirst.app.commun.fil.CibleDuFil
import fr.coprofirst.app.commun.fil.ElementDuFil
import fr.coprofirst.app.commun.fil.JourDuFil
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * #242 : l'éphéméride du Fil d'un carnet garde sa hauteur sur une page ÉTROITE. Écrasée entre ses
 * flèches, la date s'écrivait une lettre par ligne : l'éphéméride passait la hauteur de l'écran, et
 * les cartes du jour n'étaient plus composées (vu en CI, page de 144 dp).
 */
@RunWith(AndroidJUnit4::class)
class EphemerideInterfaceTest {
    @get:Rule
    val ecran = createComposeRule()

    @Test
    fun lEphemerideGardeSaHauteurSurUnePageEtroite() {
        val maintenant = Instant.now()
        val element = ElementDuFil(id = "1", type = "ticket_ouvert", date = maintenant, titre = "Fuite", detail = null, cible = CibleDuFil.Resume(null))
        ecran.setContent {
            ThemeCoproFirst(Palettes.pour(null), sombre = false) {
                Box(Modifier.width(144.dp)) { Ephemeride(JourDuFil("2026-11-17", listOf(element)), maintenant, veille = {}, lendemain = null) }
            }
        }
        val hauteur = ecran.onNodeWithTag(REPERE_EPHEMERIDE).getUnclippedBoundsInRoot().let { it.bottom - it.top }
        assertTrue("l'éphéméride fait $hauteur de haut sur 144 dp", hauteur < 200.dp)
        ecran.onNodeWithTag("fil.veille").assertIsEnabled()
        ecran.onNodeWithTag("fil.lendemain").assertIsNotEnabled()
    }
}
