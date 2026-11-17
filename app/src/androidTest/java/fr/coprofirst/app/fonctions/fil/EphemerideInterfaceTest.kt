// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.fil

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
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
 * #242 : l'éphéméride et le pied de page du Fil d'un carnet, sur une page ÉTROITE (144 dp, vue en
 * CI) comme sur une page ordinaire. Une éphéméride trop haute cachait les cartes du jour : la liste
 * ne les composait plus. Le pied de page dit « Veille » et « Lendemain » s'ils tiennent, les
 * chevrons seuls sinon — leur nom gardé pour le lecteur d'écran (comme l'iOS, coprofirst-ios#275).
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
                Box(Modifier.width(144.dp)) { Ephemeride(JourDuFil("2026-11-17", listOf(element)), maintenant) }
            }
        }
        val hauteur = ecran.onNodeWithTag(REPERE_EPHEMERIDE).getUnclippedBoundsInRoot().let { it.bottom - it.top }
        assertTrue("l'éphéméride fait $hauteur de haut sur 144 dp", hauteur < 200.dp)
    }

    @Test
    fun lePiedDePageDitVeilleEtLendemainQuandIlsTiennent() {
        pied(320.dp)
        ecran.onNodeWithText("Veille").assertExists()
        ecran.onNodeWithText("Lendemain").assertExists()
        ecran.onNodeWithTag("carnet.veille").assertIsEnabled()
        ecran.onNodeWithTag("carnet.lendemain").assertIsNotEnabled()
    }

    @Test
    fun surUnePageEtroiteLesChevronsSeulsGardentLeurNom() {
        pied(144.dp)
        ecran.onNodeWithText("Veille").assertDoesNotExist()
        ecran.onNodeWithTag("carnet.veille").assert(hasContentDescription("Veille")).assertIsEnabled()
        ecran.onNodeWithTag("carnet.lendemain").assert(hasContentDescription("Lendemain"))
        val hauteur = ecran.onNodeWithTag("carnet.veille").getUnclippedBoundsInRoot().let { it.bottom - it.top }
        assertTrue("le bouton fait $hauteur de haut", hauteur < 80.dp)
    }

    private fun pied(largeur: Dp) {
        ecran.setContent {
            ThemeCoproFirst(Palettes.pour(null), sombre = false) {
                Box(Modifier.width(largeur)) { PiedDuJour(veille = {}, lendemain = null) }
            }
        }
    }
}
