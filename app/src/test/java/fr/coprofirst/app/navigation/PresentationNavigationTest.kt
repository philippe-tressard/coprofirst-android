// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.navigation

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/** #69 : la présentation suit la largeur disponible — le seuil du « grand écran » (600 dp). */
class PresentationNavigationTest {
    @Test
    fun laLargeurChoisitLaPresentation() {
        assertEquals(PresentationNavigation.Barre, PresentationNavigation.pour(411.dp))
        assertEquals(PresentationNavigation.Barre, PresentationNavigation.pour(599.dp))
        assertEquals(PresentationNavigation.Laterale, PresentationNavigation.pour(600.dp))
        assertEquals(PresentationNavigation.Laterale, PresentationNavigation.pour(1280.dp))
    }
}
