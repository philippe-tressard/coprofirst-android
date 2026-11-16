// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.charte

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les tracés des icônes du site (#183) : chaque icône publiée se dessine, sur la grille et au
 * trait du site, et la gouttière du fil trouve celle de chaque rubrique.
 */
class TracesDuSiteTest {
    @Test
    fun chaqueIconePublieeSeDessine() {
        assertTrue(TracesDuSite.traces.size > 100)
        TracesDuSite.traces.keys.forEach { nom ->
            val icone = TracesDuSite.icone(nom)
            assertNotNull(nom, icone)
            assertEquals(nom, TracesDuSite.GRILLE, icone!!.viewportWidth)
            assertTrue(nom, icone.root.size > 0)
        }
    }

    @Test
    fun chaqueRubriqueDuFilASonTrace() {
        NaturesDuFil.parType.values.forEach { nature -> assertNotNull(nature.icone, TracesDuSite.icone(nature.icone)) }
    }

    @Test
    fun unNomInconnuNAPasDeTrace() {
        assertNull(TracesDuSite.icone("inconnue"))
        assertEquals(1.5f, TracesDuSite.EPAISSEUR)
    }
}
