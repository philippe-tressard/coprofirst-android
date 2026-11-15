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
        assertEquals("https://residence.exemple.fr", url("  Residence.Exemple.FR "))
        assertEquals("https://residence.exemple.fr", url("https://residence.exemple.fr/connexion?x=1"))
        assertEquals("https://residence.exemple.fr:8443", url("residence.exemple.fr:8443"))
    }

    @Test
    fun jamaisEnClair() {
        assertEquals("https://residence.exemple.fr", url("http://residence.exemple.fr"))
    }

    @Test
    fun ceQuiNEstPasUneAdresseEstRefuse() {
        listOf("", "   ", "residence exemple.fr", "localhost", ".exemple.fr", "exemple.", "moi@residence.exemple.fr")
            .forEach { assertNull("« $it » accepté", url(it)) }
    }

    @Test
    fun enSaisieEtRelecture() {
        val adresse = AdresseInstallation.normaliser("residence.exemple.fr:8443")!!
        assertEquals("residence.exemple.fr:8443", adresse.enSaisie)
        assertEquals("https://residence.exemple.fr:8443/api", adresse.baseApi)
        assertEquals(adresse, AdresseInstallation.relire(adresse.url))
        assertNull(AdresseInstallation.relire("http://residence.exemple.fr"))
    }
}
