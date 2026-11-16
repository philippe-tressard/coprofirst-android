// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.fil

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * L'aperçu du fil d'« En savoir plus » (#153) : son `meta` est relu comme celui d'un vrai
 * élément — si la forme servie change, l'illustration perdrait ses pastilles en silence.
 */
class ApercuDuFilTest {
    private val elements = ApercuDuFil.elements(Instant.parse("2026-11-16T10:00:00Z"))

    @Test
    fun troisCartesUneUrgenceUneActualiteDateeUneAffaireDuJour() {
        assertEquals(3, elements.size)
        val (fuite, coupure, ramonage) = elements
        assertTrue(fuite.urgent)
        assertEquals("Parking", fuite.meta.lieu)
        assertFalse(coupure.urgent)
        assertEquals("J-2", coupure.echeance?.libelle)
        assertEquals(listOf("actualite", "calendrier"), coupure.meta.natures)
        assertEquals("Jour J", ramonage.echeance?.libelle)
        assertNotNull("qui la lit", ramonage.meta.lecture)
        assertEquals("Bâtiment B", ramonage.meta.lieu)
    }

    @Test
    fun lesIdentifiantsSuiventLaFormeDuFil() {
        elements.forEach { assertTrue(it.id, it.id.startsWith("${it.type}:")) }
        assertEquals(elements.size, elements.map { it.id }.toSet().size)
    }
}
