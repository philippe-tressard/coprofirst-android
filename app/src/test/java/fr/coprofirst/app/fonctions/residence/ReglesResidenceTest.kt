// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.residence

import fr.coprofirst.app.commun.formats.FormatsDuSite
import fr.coprofirst.app.navigation.DroitsOnglet
import fr.coprofirst.app.navigation.RegistreDesMenus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Les règles de « Ma résidence », celles de l'iOS (`ResidenceTests`). */
class ReglesResidenceTest {
    private fun ids(droits: DroitsOnglet, acces: Boolean?) = OngletsResidence.ouverts(droits, acces).map { it.id }

    @Test
    fun lesDeuxMenusSontAuRegistre() {
        assertTrue("residence" in RegistreDesMenus.ecrans)
        assertTrue("annuaire" in RegistreDesMenus.ecrans)
    }

    @Test
    fun lesOngletsSuiventLesDroitsDuSite() {
        val locataire = DroitsOnglet.depuis(listOf("locataire"), null, "locataire", false)
        assertEquals(listOf("fiche"), ids(locataire, true))
        val proprietaire = DroitsOnglet.depuis(listOf("propriétaire"), null, "copropriétaire_résident", false)
        assertEquals(listOf("fiche", "carnet", "reglement"), ids(proprietaire, true))
        assertEquals(listOf("fiche"), ids(DroitsOnglet(), true))
    }

    @Test
    fun leReglementSeMasqueQuandLAdministrationNeLOuvrePas() {
        val proprietaire = DroitsOnglet(proprioOuCS = true)
        assertEquals(listOf("fiche", "carnet"), ids(proprietaire, false))
        assertTrue("tant qu'elle n'a pas répondu, l'onglet reste", "reglement" in ids(proprietaire, null))
    }

    @Test
    fun laGouttiereCoupeLaDateEnTrois() {
        assertEquals(listOf("2", "avr.", "2026"), partiesDate(FormatsDuSite.lire("2026-04-02")!!))
    }

    @Test
    fun laCompositionAjouteLesCommunsEtTotalise() {
        val contenu = ResidenceSimulee.FICHE
        assertEquals(listOf("A", "B", null), contenu.composition.map { it.first })
        assertEquals(listOf(36, 46, 47, 1), contenu.totaux)
        assertEquals(NumeroBatiment.Copropriete, contenu.numeroBatiment(null))
        assertEquals(NumeroBatiment.Inconnu, contenu.numeroBatiment(99))
        assertEquals(listOf(41), contenu.annexesDeDocument(11).map { it.id })
        assertTrue(contenu.annexesDeRapport(5).isEmpty())
    }

    @Test
    fun unAvisARenvoisSeLitSurSesExtraits() {
        val question = ResidenceSimulee.QUESTION
        assertEquals(2, question.verifies)
        assertEquals(2 to 1, question.extraitsReglement.size to question.extraitsAG.size)
        assertEquals("Règlement de copropriété — Chapitre II — p. 14", question.extraits[0].origine)
        assertEquals("sans acte ni page : la référence donnée", "Art. 4", question.extraits[1].origine)
        assertTrue(question.avecRenvois)
        assertEquals(listOf("R1", "R2", "AG1"), question.fondements.map { it.renvoi })
        assertEquals("badge-orange", question.badgeDuVerdict)
        assertFalse(ResidenceSimulee.QUESTION_AVANT.avecRenvois)
        assertEquals("badge-red", ResidenceSimulee.QUESTION_AVANT.badgeDuVerdict)
        assertEquals("un ton inconnu se lit incertain", "badge-yellow", question.copy(verdictTon = "autre").badgeDuVerdict)
    }

    @Test
    fun uneAffaireDuCarnetSeReconnaitASonLien() {
        assertEquals("29", EntreeCarnet("2026-08-02", "Fuite", "affaire", lien = "/tickets/29").affaire)
        assertEquals(null, EntreeCarnet("2026-08-02", "Contrat", "contrat", lien = "/tickets/29").affaire)
        assertEquals(null, EntreeCarnet("2026-08-02", "Fuite", "affaire", lien = "/prestataires/3").affaire)
    }
}
