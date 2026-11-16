// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.montre.cloche

import fr.coprofirst.app.fonctions.notifications.NotificationRecue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ClocheTest {
    private fun alerte(id: Int, lue: Boolean = false, urgente: Boolean = false) =
        NotificationRecue(id, "system", "Alerte $id", "", lue, urgente, Instant.parse("2026-11-16T10:00:00Z").minusSeconds(id * 60L))

    @Test
    fun `non lues d'abord, urgentes devant, ordre du serveur garde dans chaque groupe`() {
        // Le serveur sert les plus récentes d'abord : 1, 2, 3…
        val cloche = Cloche.de(
            listOf(alerte(1), alerte(2, lue = true), alerte(3, urgente = true), alerte(4), alerte(5, urgente = true), alerte(6, lue = true)),
        )
        assertEquals(listOf(3, 5, 1, 4), cloche.nonLues.map { it.id })
        assertEquals(listOf(2, 6), cloche.lues.map { it.id })
        assertEquals(2, cloche.urgentes)
    }

    @Test
    fun `une urgente deja lue ne passe pas devant et ne compte pas`() {
        val cloche = Cloche.de(listOf(alerte(1), alerte(2, lue = true, urgente = true)))
        assertEquals(listOf(1), cloche.nonLues.map { it.id })
        assertEquals(0, cloche.urgentes)
    }

    @Test
    fun `cloche vide`() {
        assertTrue(Cloche.de(emptyList()).vide)
    }

    @Test
    fun `une alerte se retrouve par son identifiant dans les deux groupes`() {
        val cloche = Cloche.de(listOf(alerte(1), alerte(2, lue = true)))
        assertEquals(2, cloche.alerte(2)?.id)
        assertEquals(1, cloche.alerte(1)?.id)
        assertNull(cloche.alerte(9))
    }
}
