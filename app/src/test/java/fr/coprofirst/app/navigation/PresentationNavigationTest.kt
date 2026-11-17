// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.navigation

import androidx.compose.ui.unit.dp
import fr.coprofirst.app.commun.miseenpage.CoupeDeFenetre
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** #69 : la présentation suit la largeur disponible — le seuil du « grand écran » (600 dp) ; #242 : et le pli. */
class PresentationNavigationTest {
    @Test
    fun laLargeurChoisitLaPresentation() {
        assertEquals(PresentationNavigation.Barre, PresentationNavigation.pour(411.dp))
        assertEquals(PresentationNavigation.Barre, PresentationNavigation.pour(599.dp))
        assertEquals(PresentationNavigation.Laterale, PresentationNavigation.pour(600.dp))
        assertEquals(PresentationNavigation.Laterale, PresentationNavigation.pour(1280.dp))
    }

    /** #242 : un pliable déplié EN LIVRE (pli vertical) est un carnet, quelle que soit sa largeur. */
    @Test
    fun lePliEnLivreFaitUnCarnet() {
        val livre = Posture.depuis(true, listOf(Pli(vertical = true, gauche = 1000, droite = 1040, aPlat = true)))
        assertEquals(PresentationNavigation.Carnet, PresentationNavigation.pour(840.dp, livre))
        assertEquals(PresentationNavigation.Carnet, PresentationNavigation.pour(500.dp, livre))
        // À plat sur la table (pli horizontal), ou sans pli : la largeur décide, comme avant.
        val table = Posture.depuis(true, listOf(Pli(vertical = false, gauche = 0, droite = 2000, aPlat = false)))
        assertEquals(PresentationNavigation.Laterale, PresentationNavigation.pour(840.dp, table))
        assertEquals(PresentationNavigation.Barre, PresentationNavigation.pour(411.dp, Posture.depuis(true, emptyList())))
    }

    @Test
    fun laDoublePageSeCoupeAuPliVerticalSeulement() {
        val livre = Posture.depuis(true, listOf(Pli(vertical = true, gauche = 1000, droite = 1040, aPlat = true)))
        assertEquals(CoupeDeFenetre(1000, 1040), livre.coupe())
        assertNull(Posture.depuis(true, listOf(Pli(vertical = false, gauche = 0, droite = 2000, aPlat = false))).coupe())
        assertNull(Posture().coupe())
    }
}
