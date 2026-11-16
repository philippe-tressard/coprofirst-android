// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.affaires

import fr.coprofirst.app.commun.perimetres.Perimetre
import fr.coprofirst.app.session.ErreurService
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** L'état partagé des trois onglets : un geste refusé ramène la carte où elle était, et se dit. */
class EtatAffairesTest {
    private class Simule(var refuser: Boolean = false) : ServiceAffaires {
        val deplacements = mutableListOf<Pair<Int, String>>()
        var affaires = listOf(
            affaire(1, "ouvert", "cs"), affaire(2, "en_cours", "syndic"),
        )

        override suspend fun affaires() = affaires
        override suspend fun referentiels() = REFERENTIELS
        override suspend fun rechercher(terme: String) = listOf(2)
        override suspend fun offrirMeConcerne() = false
        override suspend fun candidatesALaFusion(id: Int) = 0
        override suspend fun deplacer(id: Int, statut: String) {
            if (refuser) throw ErreurService.Refus("Réservé au conseil syndical.", 403)
            deplacements += id to statut
        }
        override suspend fun archiver(id: Int): Affaire {
            if (refuser) throw ErreurService.Injoignable
            return affaires.first { it.id == id }.copy(archivee = true, prestataires = emptyList())
        }
        override suspend fun affaire(id: Int) = throw ErreurService.Introuvable
        override suspend fun perimetres() = emptyList<Perimetre>()
        override suspend fun lieu(codes: List<String>) = ""
        override suspend fun creer(saisie: SaisieAffaire) = throw ErreurService.Inattendue
        override suspend fun repondre(id: Int, texte: String) = Unit
        override suspend fun ajouterSuite(id: Int, suite: SaisieSuite) = Unit
        override suspend fun regler(id: Int, option: OptionAffaire, active: Boolean) = Unit
        override suspend fun enregistrer(id: Int, edition: EditionAffaire) = Unit
        override suspend fun corrigerSuite(id: Int, suite: Int, correction: CorrectionSuite): IssueCorrection? = null
        override suspend fun supprimerSuite(id: Int, suite: Int) = Unit
    }

    @Test
    fun deplacerChangeLaColonneEtLeLibelleServi() = runTest {
        val service = Simule()
        val etat = EtatAffaires(service).apply { charger() }
        val ok = etat.deplacer(etat.affaires[0], REFERENTIELS.statutPourColonne("syndic")!!)
        assertTrue(ok)
        assertEquals(listOf(1 to "en_cours"), service.deplacements)
        assertEquals("Chez le syndic", etat.affaires[0].statutLibelle)
        assertEquals(listOf(1, 2), etat.cartes(ColonneKanban("syndic", "", "")).map { it.id })
    }

    @Test
    fun unDeplacementRefuseRameneLaCarteEtSeDit() = runTest {
        val etat = EtatAffaires(Simule(refuser = true)).apply { charger() }
        val avant = etat.affaires[0]
        assertEquals(false, etat.deplacer(avant, REFERENTIELS.statutPourColonne("syndic")!!))
        assertEquals(avant, etat.affaires[0])
        assertEquals("Réservé au conseil syndical.", (etat.avis as ErreurService.Refus).phraseServie)
    }

    @Test
    fun unArchivageGardeLesPrestatairesLus() = runTest {
        val etat = EtatAffaires(Simule().apply { affaires = affaires.map { it.copy(prestataires = listOf("Ascenseurs du Parc")) } }).apply { charger() }
        etat.archiver(etat.affaires[1])
        assertTrue(etat.affaires[1].archivee)
        assertEquals(listOf("Ascenseurs du Parc"), etat.affaires[1].prestataires)
    }

    @Test
    fun unArchivageRefuseRameneLaCarte() = runTest {
        val etat = EtatAffaires(Simule(refuser = true)).apply { charger() }
        etat.archiver(etat.affaires[1])
        assertEquals(false, etat.affaires[1].archivee)
        assertEquals(ErreurService.Injoignable, etat.avis)
    }

    @Test
    fun unFiltreSurUnEtatDisparuRetombeSurToutes() = runTest {
        val etat = EtatAffaires(Simule()).apply { charger() }
        etat.filtres = FiltresAffaires(suivi = "ouvert")
        etat.deplacer(etat.affaires[0], REFERENTIELS.statutPourColonne("syndic")!!)
        assertNull(etat.filtres.suivi)
    }

    @Test
    fun uneRechercheTropCourteNInterrogePasLeServeur() = runTest {
        val etat = EtatAffaires(Simule()).apply { charger() }
        etat.recherche = "f"
        etat.chercher()
        assertNull(etat.trouvees)
        etat.recherche = "fuite"
        etat.chercher()
        assertEquals(listOf(2), etat.trouvees)
    }

    private companion object {
        fun affaire(id: Int, statut: String, colonne: String) = Affaire(
            id = id, numero = "A-$id", titre = "Affaire $id", apercu = "", creeLe = Instant.EPOCH, statut = statut,
            statutLibelle = statut, categorie = "panne", categorieLibelle = "Panne", colonne = colonne, suiviKanban = true,
            peutDeplacer = true, peutArchiver = true,
        )

        val REFERENTIELS = ReferentielsAffaires(
            listOf(ColonneKanban("cs", "CS", "CS"), ColonneKanban("syndic", "Syndic", "Syndic")),
            listOf(
                ReferentielsAffaires.Statut("ouvert", "Ouvert", false, "cs", true),
                ReferentielsAffaires.Statut("en_cours", "Chez le syndic", false, "syndic", true),
            ),
            emptyList(),
        )
    }
}
