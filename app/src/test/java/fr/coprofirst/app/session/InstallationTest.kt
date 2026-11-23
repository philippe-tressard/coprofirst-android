// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.session

import fr.coprofirst.api.VERSION_CONTRAT_PLANCHER
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

    private fun servi(v: String?) = Installation("https://glycines.exemple.test", "Les Glycines", versionContrat = v)

    /**
     * #282 : une porte au-dessus du plancher s'ouvre au contrat servi qui l'atteint, jamais sans
     * contrat lisible. Aucune n'est ouverte depuis #311 : une porte fictive éprouve le mécanisme.
     */
    @Test
    fun unePorteSOuvreAuContratQuiLAtteint() {
        val porte = VersionContrat(plancher.majeure, plancher.mineure + 1, 0)
        assertTrue(servi(porte.toString()).sert(porte))
        assertTrue(servi(VersionContrat(plancher.majeure + 1, 0, 0).toString()).sert(porte))
        assertFalse(servi(plancher.toString()).sert(porte))
        assertFalse(servi(null).sert(porte))
    }

    /**
     * #311 : la modération (contrat 2.9.0, coprofirst#2861) et le relais des push (2.10.0,
     * coprofirst#2789) sont AU PLANCHER — offerts sans porte à toute installation qui dit son
     * contrat ; un contrat absent ou illisible n'ouvre rien, comme l'iOS.
     */
    @Test
    fun laModerationEtLeRelaisSontOffertsAuPlancher() {
        assertTrue("le plancher ne sert plus la modération : lui remettre une porte", plancher >= VersionContrat(2, 9, 0))
        assertTrue("le plancher ne relaie plus FCM : lui remettre une porte", plancher >= VersionContrat(2, 10, 0))
        assertTrue(servi(VERSION_CONTRAT_PLANCHER).sertLePlancher)
        assertTrue(servi("99.0.0").sertLePlancher)
        assertFalse("contrat absent", servi(null).sertLePlancher)
        assertFalse("contrat illisible", servi("2.10").sertLePlancher)
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
