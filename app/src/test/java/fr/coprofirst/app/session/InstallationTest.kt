// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        // La version juste sous le plancher, quel qu'il soit (2.0.0 → 1.99.99).
        val dessous = when {
            plancher.correctif > 0 -> VersionContrat(plancher.majeure, plancher.mineure, plancher.correctif - 1)
            plancher.mineure > 0 -> VersionContrat(plancher.majeure, plancher.mineure - 1, 99)
            else -> VersionContrat(plancher.majeure - 1, 99, 99)
        }
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

    /** #282 : une porte au-dessus du plancher s'ouvre au contrat servi qui l'atteint, jamais sans contrat lisible. */
    @Test
    fun unePorteSOuvreAuContratQuiLAtteint() {
        fun servi(v: String?) = Installation("https://glycines.exemple.test", "Les Glycines", versionContrat = v)
        assertTrue(servi("2.9.0").sert(VersionContrat.MODERATION))
        assertTrue(servi("2.10.0").sert(VersionContrat.MODERATION))
        assertFalse(servi("2.8.0").sert(VersionContrat.MODERATION))
        assertFalse(servi(null).sert(VersionContrat.MODERATION))
        assertTrue(servi("2.10.0").sert(VersionContrat.PUSH))
        assertFalse(servi("2.9.0").sert(VersionContrat.PUSH))
    }

    /** Une porte que le plancher atteint est MORTE : elle se retire, avec la greffe de `GREFFES`. */
    @Test
    fun aucunePorteSousLePlancher() {
        assertTrue("porte morte : le plancher sert la modération — retirer MODERATION et la greffe", plancher < VersionContrat.MODERATION)
        assertTrue("porte morte : le plancher relaie FCM — retirer PUSH", plancher < VersionContrat.PUSH)
    }

    /** #233 : `installation_suspendue` est une CHAÎNE ; « 1 » seul suspend ; absente, rien. */
    @Test
    fun laSuspensionSeLitDansLaChaineServie() {
        fun suspendue(valeur: String?) =
            Installation.depuisConfig(adresse, mapOf("site_nom" to "Les Glycines", "installation_suspendue" to valeur)).suspendue
        assertTrue(suspendue("1"))
        assertTrue(suspendue(" 1 "))
        assertFalse(suspendue("0"))
        assertFalse(suspendue(null))
        assertFalse("un booléen JSON n'est pas la forme servie", suspendue("true"))
        assertFalse(Installation.depuisConfig(adresse, mapOf("site_nom" to "Les Glycines")).suspendue)
    }
}
