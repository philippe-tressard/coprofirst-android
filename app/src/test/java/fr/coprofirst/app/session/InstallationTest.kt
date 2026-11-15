// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class InstallationTest {
    private val adresse = AdresseInstallation.normaliser("residence.exemple.test")!!
    private val plancher = VersionContrat.PLANCHER

    private fun refus(cles: Map<String, String?>): ErreurService = try {
        Installation.depuisConfig(adresse, cles)
        fail("acceptée : $cles")
        error("inaccessible")
    } catch (erreur: ErreurService) {
        erreur
    }

    @Test
    fun versionsSeComparentParLeursNombres() {
        assertTrue(VersionContrat.lire("1.10.0")!! > VersionContrat.lire("1.9.9")!!)
        assertEquals(VersionContrat(1, 27, 0), VersionContrat.lire(" 1.27.0 "))
        listOf(null, "", "1.27", "v1.27.0", "1.27.0-rc1").forEach { assertNull(VersionContrat.lire(it)) }
    }

    @Test
    fun configDUneResidence() {
        val installation = Installation.depuisConfig(
            adresse,
            mapOf("site_nom" to "Les Glycines", "version_contrat" to plancher.toString(), "autre" to "x"),
        )
        assertEquals(Installation("https://residence.exemple.test", "Les Glycines", plancher.toString()), installation)
    }

    @Test
    fun laPaletteServieEstGardee() {
        val avec = Installation.depuisConfig(adresse, mapOf("site_nom" to "Prune", "site_palette" to "prune"))
        assertEquals("prune", avec.codePalette)
        assertNull(Installation.depuisConfig(adresse, mapOf("site_nom" to "Sans", "site_palette" to " ")).codePalette)
    }

    @Test
    fun sansNomCeNEstPasCoproFirst() {
        assertSame(ErreurService.PasCoproFirst, refus(emptyMap()))
        assertSame(ErreurService.PasCoproFirst, refus(mapOf("site_nom" to "  ")))
    }

    @Test
    fun contratSousLePlancherRefuse() {
        val dessous = VersionContrat(plancher.majeure, plancher.mineure, plancher.correctif - 1)
            .takeIf { plancher.correctif > 0 } ?: VersionContrat(plancher.majeure, plancher.mineure - 1, 99)
        assertSame(
            ErreurService.ResidenceAMettreAJour,
            refus(mapOf("site_nom" to "Ancienne", "version_contrat" to dessous.toString())),
        )
    }

    @Test
    fun contratAbsentOuPlusRecentAccepte() {
        Installation.depuisConfig(adresse, mapOf("site_nom" to "Sans version"))
        Installation.depuisConfig(adresse, mapOf("site_nom" to "Récente", "version_contrat" to "99.0.0"))
    }
}
