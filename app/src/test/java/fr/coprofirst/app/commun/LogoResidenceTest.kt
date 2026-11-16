// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun

import fr.coprofirst.app.session.AdresseInstallation
import fr.coprofirst.app.session.Installation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Le logo de la résidence (#137), comme l'iOS (`Installation.adresseLogo`, `LogosEnMemoire`). */
class LogoResidenceTest {
    private val adresse = checkNotNull(AdresseInstallation.normaliser("residence.exemple.test"))

    private fun installation(logo: String? = "/uploads/logo.png", sombre: String? = null) =
        Installation.depuisConfig(adresse, mapOf("site_nom" to "Les Glycines", "site_logo" to logo, "site_logo_sombre" to sombre))

    @Test
    fun laConfigDitSiLaResidenceAUnLogoEtUneVarianteSombre() {
        assertTrue(installation().aUnLogo)
        assertFalse(installation().aUnLogoSombre)
        assertTrue(installation(sombre = "/uploads/logo-sombre.png").aUnLogoSombre)
        assertFalse(installation(logo = "").aUnLogo)
        assertFalse(installation(logo = null).aUnLogo)
    }

    /** La route du contrat (GET /config/logo.png), lue de la configuration GÉNÉRÉE ; la variante sombre si elle est servie. */
    @Test
    fun lAdresseDuLogoSuitLeTheme() {
        assertEquals("https://residence.exemple.test/api/config/logo.png", LogosEnMemoire.adresse(installation(), sombre = false))
        assertEquals("sans variante servie : le logo clair", "https://residence.exemple.test/api/config/logo.png", LogosEnMemoire.adresse(installation(), sombre = true))
        assertEquals(
            "https://residence.exemple.test/api/config/logo.png?variante=sombre",
            LogosEnMemoire.adresse(installation(sombre = "/uploads/s.png"), sombre = true),
        )
        assertNull("sans logo : celui de CoproFirst", LogosEnMemoire.adresse(installation(logo = null), sombre = false))
    }

    /** Lu une fois par adresse, gardé en mémoire ; un échec se dit `null` et se retente. */
    @Test
    fun unLogoSeLitUneFois() = runTest {
        val lus = mutableListOf<String>()
        var reussir = false
        val logos = LogosEnMemoire<String> { url -> lus += url; if (reussir) "image de $url" else null }
        assertNull(logos.charger("a"))
        reussir = true
        assertEquals("image de a", logos.charger("a"))
        assertEquals("image de a", logos.charger("a"))
        assertEquals(listOf("a", "a"), lus)
        assertEquals("image de a", logos.garde("a"))
    }
}
