// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AdresseInstallationTest {
    private fun url(saisie: String) = AdresseInstallation.normaliser(saisie)?.url

    @Test
    fun saisieRameneeAHttpsEtALHote() {
        assertEquals("https://residence.exemple.test", url("  Residence.Exemple.TEST "))
        assertEquals("https://residence.exemple.test", url("https://residence.exemple.test/connexion?x=1"))
        assertEquals("https://residence.exemple.test:8443", url("residence.exemple.test:8443"))
    }

    @Test
    fun jamaisEnClair() {
        assertEquals("https://residence.exemple.test", url("http://residence.exemple.test"))
    }

    @Test
    fun ceQuiNEstPasUneAdresseEstRefuse() {
        listOf("", "   ", "residence exemple.test", "localhost", ".exemple.test", "exemple.", "moi@residence.exemple.test")
            .forEach { assertNull("« $it » accepté", url(it)) }
    }

    @Test
    fun enSaisieEtRelecture() {
        val adresse = AdresseInstallation.normaliser("residence.exemple.test:8443")!!
        assertEquals("residence.exemple.test:8443", adresse.enSaisie)
        assertEquals("https://residence.exemple.test:8443/api", adresse.baseApi)
        assertEquals(adresse, AdresseInstallation.relire(adresse.url))
        assertNull(AdresseInstallation.relire("http://residence.exemple.test"))
    }
}
