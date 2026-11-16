// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.entree

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Un lien d'application ne désigne une résidence que par HTTPS, sans rien de plus. */
class LienDApplicationTest {
    @Test
    fun laResidenceDUnLienSeLitParSonHote() {
        assertEquals("https://residence.exemple.test", LienDApplication.adresse("https://Residence.exemple.test/tickets/12#msg-4")?.url)
        assertEquals("https://residence.exemple.test", LienDApplication.adresse("https://residence.exemple.test")?.url)
        assertEquals("https://residence.exemple.test:8443", LienDApplication.adresse("https://residence.exemple.test:8443/actualites")?.url)
    }

    @Test
    fun ceQuiNEstPasUnLienHttpsSurUnHoteNeDesigneRien() {
        assertNull(LienDApplication.adresse("http://residence.exemple.test/"))
        assertNull(LienDApplication.adresse("coprofirst://residence.exemple.test/"))
        assertNull(LienDApplication.adresse("https://moi@residence.exemple.test/"))
        assertNull(LienDApplication.adresse("https:///tickets"))
        assertNull(LienDApplication.adresse("https://localhost/"))
        assertNull(LienDApplication.adresse("pas un lien"))
    }
}
