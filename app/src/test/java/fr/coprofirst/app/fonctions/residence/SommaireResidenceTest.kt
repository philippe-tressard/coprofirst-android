// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.residence

import fr.coprofirst.app.navigation.DroitsOnglet
import org.junit.Assert.assertEquals
import org.junit.Test

/** #287, comme `SommaireResidenceTests` de l'iOS (coprofirst-ios#278) : le sommaire de « Ma résidence » dans le carnet. */
class SommaireResidenceTest {
    private fun cles(droits: DroitsOnglet, acces: Boolean? = true) =
        PageDuSommaire.sommaire(droits.locataire, OngletsResidence.ouverts(droits, acces)).map { it.cle }

    /** Les rubriques de la Fiche dans l'ordre du site, puis le Carnet d'entretien et le Règlement. */
    @Test
    fun leSommaireDuProprietaire() {
        assertEquals(
            listOf(
                "rubrique.identite", "rubrique.regles", "rubrique.plans", "rubrique.reglement",
                "rubrique.comptesRendus", "rubrique.diagnostics", "onglet.carnet", "onglet.reglement",
            ),
            cles(DroitsOnglet(proprioOuCS = true)),
        )
    }

    /** La réserve du locataire, écrite une fois : ni comptes-rendus d'AG, ni diagnostics, ni carnet, ni règlement. */
    @Test
    fun leLocataireNeLitQueSesRubriques() {
        assertEquals(
            listOf("rubrique.identite", "rubrique.regles", "rubrique.plans", "rubrique.reglement"),
            cles(DroitsOnglet(locataire = true)),
        )
        assertEquals(RubriqueFiche.ouvertes(locataire = true), RubriqueFiche.entries - RubriqueFiche.ComptesRendus - RubriqueFiche.Diagnostics)
    }

    @Test
    fun leReglementFermeParLAdministrationDisparait() {
        assertEquals("onglet.carnet", cles(DroitsOnglet(proprioOuCS = true), acces = false).last())
    }

    /** La page gardée par son nom ; une page retirée, ou rien de gardé : la première, jamais « Aucune sélection ». */
    @Test
    fun laPageChoisie() {
        val sommaire = PageDuSommaire.sommaire(false, OngletsResidence.ouverts(DroitsOnglet(proprioOuCS = true), true))
        assertEquals("rubrique.plans", PageDuSommaire.choisie(sommaire, "rubrique.plans")?.cle)
        assertEquals("rubrique.identite", PageDuSommaire.choisie(sommaire, null)?.cle)
        assertEquals("rubrique.identite", PageDuSommaire.choisie(sommaire, "onglet.disparu")?.cle)
        assertEquals("rubrique.identite", PageDuSommaire.choisie(sommaire, "2026-11-17")?.cle)
    }
}
