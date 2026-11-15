// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.prestataires

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Les règles d'affichage de « Prestataires » : celles de l'iOS, sans règle métier recopiée. */
class ReglesPrestatairesTest {
    private val ascenseurs = Prestataire(
        1, "Ascenseurs du Parc", "ascenseur", "maintenance_depannage",
        description = "<p>Astreinte <strong>24 h/24</strong></p>",
        contacts = listOf(ContactPrestataire(fonction = "Astreinte", telephone = "01 99 00 00 13")),
    )
    private val controles = Prestataire(2, "Contrôles Sécurité", "incendie", "reglementaire")
    private val inconnu = Prestataire(3, "Sans catégorie", "eau", "categorie_future")
    private val donnees = DonneesPrestataires(
        listOf(ascenseurs, controles, inconnu),
        listOf(
            Contrat(1, 1, "Maintenance", "ascenseur", "2024-01-01", prochaineVisite = "2026-11-18"),
            Contrat(2, 2, "Extincteurs", "incendie", "2023-06-01"),
            Contrat(3, 2, "Alarme", "incendie", "2023-06-01", prochaineVisite = "2026-09-30"),
            Contrat(4, 3, "Eau", "zz_inconnu", "2020-01-01"),
        ),
        listOf(Equipement("incendie", "Sécurité incendie", 1), Equipement("ascenseur", "Ascenseurs", 2)),
    )

    @Test
    fun laRechercheLitCeQueLaFicheAffiche() {
        assertTrue("tous les mots, sans accents ni ordre", ReglesPrestataires.correspond(controles, "Sécurité incendie", "incendie controles"))
        assertTrue("le texte riche, sans balises", ReglesPrestataires.correspond(ascenseurs, "Ascenseurs", "24 h/24"))
        assertTrue("un contact", ReglesPrestataires.correspond(ascenseurs, "Ascenseurs", "astreinte"))
        assertFalse(ReglesPrestataires.correspond(ascenseurs, "Ascenseurs", "incendie"))
    }

    @Test
    fun lesFiltresSeCumulent() {
        val tout = donnees.prestataires
        assertEquals(listOf(2), ReglesPrestataires.filtrer(tout, donnees, "reglementaire", null, "").map { it.id })
        assertEquals(listOf(1, 2, 3), ReglesPrestataires.filtrer(tout, donnees, null, "sous_contrat", "").map { it.id })
        assertEquals(emptyList<Int>(), ReglesPrestataires.filtrer(tout, donnees, null, "sans_contrat", "").map { it.id })
        assertEquals(listOf(1), ReglesPrestataires.filtrer(tout, donnees, "maintenance_depannage", "sous_contrat", "parc").map { it.id })
    }

    @Test
    fun lesCategoriesSuiventLOrdreDuSiteEtLInconnueVaALaFin() {
        val groupes = ReglesPrestataires.parCategorie(listOf(controles, inconnu, ascenseurs))
        assertEquals(listOf("maintenance_depannage", "reglementaire", null), groupes.map { it.first?.code })
        assertEquals("une catégorie inconnue s'affiche brute", "categorie_future", ReglesPrestataires.type("categorie_future"))
        assertEquals("—", ReglesPrestataires.type(null))
    }

    @Test
    fun lesContratsParEquipementServiPuisParEcheance() {
        val groupes = ReglesPrestataires.groupes(donnees)
        assertEquals(listOf("incendie", "ascenseur", "zz_inconnu"), groupes.map { it.first })
        assertEquals("la visite la plus proche d'abord, sans échéance à la fin", listOf(3, 2), groupes.first().second.map { it.id })
        assertEquals(2, ReglesPrestataires.sansEcheance(donnees.contrats))
    }

    @Test
    fun lesRelevesParAnneeLesPlusRecentesDAbord() {
        val releves = listOf(Releve(1, "2025-03-01"), Releve(2, "2026-01-01"), Releve(3, "2025-12-01"), Releve(4, "illisible"))
        val annees = ReglesPrestataires.parAnnee(releves)
        assertEquals(listOf(2026, 2025, null), annees.map { it.first })
        assertEquals("dans une année, l'ordre de l'API", listOf(1, 3), annees[1].second.map { it.id })
    }

    @Test
    fun unLienDAppelNeGardeQueLesChiffres() {
        assertEquals("+33199000013", ReglesPrestataires.numeroAppel("+33 1 99 00 00 13"))
        assertNull(ReglesPrestataires.numeroAppel("à demander"))
        assertNull(ReglesPrestataires.numeroAppel(null))
    }
}
