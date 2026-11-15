// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.charte

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Les palettes GÉNÉRÉES depuis la charte publiée : la charte par défaut, une par résidence. */
class PalettesTest {
    @Test
    fun laChartePourUnCodeAbsentOuInconnu() {
        assertEquals(Palettes.CHARTE, Palettes.pour(null).code)
        assertEquals(Palettes.CHARTE, Palettes.pour("inexistante").code)
    }

    @Test
    fun chaquePaletteADeuxModesDistincts() {
        assertTrue("la charte et au moins une palette", Palettes.parCode.size >= 2)
        Palettes.parCode.forEach { (code, palette) ->
            assertEquals(code, palette.code)
            assertNotEquals("$code : clair et sombre identiques", palette.clair.bg, palette.sombre.bg)
        }
    }

    @Test
    fun uneResidenceChangeLaCouleurPrincipale() {
        val autres = Palettes.parCode.values.filter { it.code != Palettes.CHARTE }
        assertTrue(autres.all { it.clair.primary != Palettes.pour(null).clair.primary })
    }
}
