// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.montre.telephone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LienSurLeTelephoneTest {
    private val adresse = "https://residence.exemple.test"

    @Test
    fun `un chemin du site s'ouvre sous l'adresse de la residence`() {
        assertEquals("https://residence.exemple.test/tickets/12", LienSurLeTelephone.url(adresse, "/tickets/12"))
        assertEquals("https://residence.exemple.test/tickets/12#msg-3", LienSurLeTelephone.url("$adresse/", "/tickets/12#msg-3"))
    }

    @Test
    fun `sans lien, rien a ouvrir`() {
        assertNull(LienSurLeTelephone.url(adresse, null))
        assertNull(LienSurLeTelephone.url(adresse, ""))
    }

    @Test
    fun `un lien qui viserait un autre hote ne s'ouvre pas`() {
        assertNull(LienSurLeTelephone.url(adresse, "//autre.exemple.test/x"))
        assertNull(LienSurLeTelephone.url(adresse, "https://autre.exemple.test/x"))
        assertNull(LienSurLeTelephone.url(adresse, "/\\autre.exemple.test"))
        assertNull(LienSurLeTelephone.url(adresse, "@autre.exemple.test/x"))
        assertNull(LienSurLeTelephone.url(adresse, "/x y"))
    }

    @Test
    fun `une adresse qui n'est pas en https ne s'ouvre pas`() {
        assertNull(LienSurLeTelephone.url("http://residence.exemple.test", "/tickets/12"))
    }
}
