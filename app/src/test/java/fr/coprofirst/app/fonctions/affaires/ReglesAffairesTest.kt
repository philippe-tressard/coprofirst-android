// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.affaires

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/** Les règles des trois onglets, comme celles du site et de l'iOS (`AffairesTests`). */
class ReglesAffairesTest {
    private fun affaire(
        id: Int,
        statut: String = "ouvert",
        archivee: Boolean = false,
        le: String = "2026-11-10T10:00:00Z",
        natures: List<String> = listOf("activite"),
        meConcerne: Boolean? = null,
        colonne: String? = null,
        suivie: Boolean = false,
    ) = Affaire(
        id = id, numero = "A-${id.toString().padStart(4, '0')}", titre = "Affaire $id", apercu = "",
        creeLe = Instant.parse(le), statut = statut, statutLibelle = statut, categorie = "panne", categorieLibelle = "Panne",
        archivee = archivee, natures = natures, meConcerne = meConcerne, colonne = colonne, suiviKanban = suivie,
    )

    private val servis = listOf(
        ReferentielsAffaires.Statut("ouvert", "Ouvert", false, "cs", true),
        ReferentielsAffaires.Statut("en_ag", "À l'AG", false, "ag", true),
        ReferentielsAffaires.Statut("en_cours", "Chez le syndic", false, "syndic", true),
        ReferentielsAffaires.Statut("résolu", "Résolu", true, "termine", true),
        ReferentielsAffaires.Statut("fermé", "Fermé", true, null, false),
    )

    @Test
    fun lesOptionsDeSuiviSontCellesDesStatutsPresentsDansLOrdreServi() {
        val options = OptionDeSuivi.presentes(listOf(affaire(1, "résolu"), affaire(2, "en_cours"), affaire(3, "en_ag"), affaire(4, "fermé")), servis)
        // Le groupe « En cours » réunit en_ag et en_cours ; « fermé » n'est pas proposable.
        assertEquals(listOf("en_cours", "résolu"), options.map { it.code })
        assertEquals("En cours", options.first().libelle)
    }

    @Test
    fun laListeSansRechercheTaitLesArchivesEtFiltre() {
        val affaires = listOf(
            affaire(1), affaire(2, archivee = true), affaire(3, "en_cours", natures = listOf("actualite")),
            affaire(4, meConcerne = false), affaire(5, meConcerne = true),
        )
        val options = OptionDeSuivi.presentes(affaires, servis)
        assertEquals(listOf(1, 3, 4, 5), ReglesAffaires.liste(affaires, FiltresAffaires(), options, null, false).map { it.id })
        assertEquals(listOf(3), ReglesAffaires.liste(affaires, FiltresAffaires(suivi = "en_cours"), options, null, false).map { it.id })
        assertEquals(listOf(3), ReglesAffaires.liste(affaires, FiltresAffaires(nature = "actualite"), options, null, false).map { it.id })
        // « Ce qui me concerne » garde ce que le serveur ne sait pas (`null`).
        assertEquals(listOf(1, 3, 5), ReglesAffaires.liste(affaires, FiltresAffaires(meConcerne = true), options, null, false).map { it.id })
    }

    @Test
    fun laRechercheGardeLOrdreDuServeurEtNOffreLesArchivesQueSurDemande() {
        val affaires = listOf(affaire(1), affaire(2, archivee = true), affaire(3))
        assertEquals(listOf(3, 1), ReglesAffaires.liste(affaires, FiltresAffaires(), emptyList(), listOf(3, 2, 1, 99), false).map { it.id })
        assertEquals(listOf(3, 2, 1), ReglesAffaires.liste(affaires, FiltresAffaires(), emptyList(), listOf(3, 2, 1), true).map { it.id })
    }

    @Test
    fun lesArchivesSeRangentParAnneeSurTroisAns() {
        val au = Instant.parse("2026-11-15T12:00:00Z")
        val affaires = listOf(
            affaire(1, archivee = true, le = "2026-03-01T10:00:00Z"),
            affaire(2, archivee = true, le = "2025-12-31T23:30:00Z"), // 2026 à Paris
            affaire(3, archivee = true, le = "2024-06-01T10:00:00Z"),
            affaire(4, archivee = true, le = "2022-06-01T10:00:00Z"), // plus de trois ans
            affaire(5, archivee = false, le = "2026-01-01T10:00:00Z"),
        )
        val archives = ReglesAffaires.archives(affaires, au)
        assertEquals(listOf(2026, 2024), archives.map { it.annee })
        assertEquals(listOf(1, 2), archives.first().affaires.map { it.id })
    }

    @Test
    fun leKanbanPrendLesSuiviesNonArchiveesAvecUneColonne() {
        val affaires = listOf(
            affaire(1, colonne = "cs", suivie = true), affaire(2, colonne = "cs", suivie = false),
            affaire(3, colonne = null, suivie = true), affaire(4, colonne = "ag", suivie = true, archivee = true),
        )
        assertEquals(listOf(1), ReglesAffaires.auKanban(affaires).map { it.id })
    }

    @Test
    fun uneCarteVaOuUnEtatServiMeneSaufDansSaColonne() {
        val referentiels = ReferentielsAffaires(
            listOf(ColonneKanban("cs", "CS", "CS"), ColonneKanban("ag", "AG", "AG"), ColonneKanban("annule", "Annulé", "Annulé")),
            servis, emptyList(),
        )
        val destinations = ReglesAffaires.destinations(affaire(1, colonne = "cs", suivie = true), referentiels)
        assertEquals(listOf("ag"), destinations.map { it.id })
        assertEquals(emptyList<ColonneKanban>(), ReglesAffaires.destinations(affaire(1), null))
    }

    @Test
    fun unEtatSeDitServiPuisParLesReferentielsPuisParSonCode() {
        val r = ReferentielsAffaires(emptyList(), servis, emptyList())
        assertEquals("Chez le syndic !", r.libelle("en_cours", "Chez le syndic !"))
        assertEquals("Chez le syndic", r.libelle("en_cours", ""))
        assertEquals("inconnu", r.libelle("inconnu"))
        assertNull(r.statutPourColonne("fournisseur"))
    }

    @Test
    fun lesCategoriesPermisesMettentLaTeteDAbord() {
        val r = ReferentielsAffaires(
            emptyList(), emptyList(),
            listOf(
                ReferentielsAffaires.Categorie("panne", "Panne", "🛠️", permise = true),
                ReferentielsAffaires.Categorie("actualite", "Actualité", "📰", permise = true, enTete = true, reservee = true),
                ReferentielsAffaires.Categorie("travaux", "Travaux", "🏗️", permise = false),
            ),
        )
        assertEquals(listOf("actualite", "panne"), r.permises.map { it.code })
        assertEquals(true, r.poseLesReservees)
    }
}
