// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.notifications

import fr.coprofirst.api.modeles.NotificationRead
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

/** `NotificationRecue.depuis` est la seule lecture de `NotificationRead` : chaque champ servi y passe. */
class NotificationRecueTest {
    private val creeLe = OffsetDateTime.of(2026, 11, 16, 14, 32, 0, 0, ZoneOffset.ofHours(1))

    private fun servie(lien: String?) =
        NotificationRead(id = 12, type = "ticket_update", titre = "Votre affaire avance", corps = "<p>x</p>",
            lue = false, urgente = true, creeLe = creeLe, lien = lien)

    @Test
    fun `chaque champ servi est lu, le lien compris`() {
        val alerte = NotificationRecue.depuis(servie("/tickets/12"))
        assertEquals(
            NotificationRecue(12, "ticket_update", "Votre affaire avance", "<p>x</p>", false, true,
                Instant.parse("2026-11-16T13:32:00Z"), "/tickets/12"),
            alerte,
        )
    }

    @Test
    fun `une alerte sans lien n'en a pas`() {
        assertNull(NotificationRecue.depuis(servie(null)).lien)
    }
}
