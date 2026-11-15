// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.affaires

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** La saisie et les gestes d'une affaire, comme l'iOS (`SaisieAffaireTests`, `BandeauAffaireTests`). */
class SaisieAffaireTest {
    private fun fiche(droits: DroitsServisAffaire, categorie: String = "panne", archivee: Boolean = false, fusionnee: Boolean = false) =
        DetailAffaire(
            id = 1, numero = "A-1", titre = "T", description = "", categorie = categorie, statut = "ouvert", creeLe = Instant.EPOCH,
            droits = droits, archivee = archivee, fusionnee = fusionnee,
        )

    @Test
    fun uneSaisieEstPreteAvecCategorieTitreEtDescription() {
        assertFalse(SaisieAffaire(titre = "Fuite", description = "Au local").complete)
        assertFalse(SaisieAffaire(categorie = "panne", titre = "  ", description = "Au local").complete)
        assertTrue(SaisieAffaire(categorie = "panne", titre = "Fuite", description = "Au local").complete)
        assertTrue(SaisieAffaire(titre = "F").entamee)
    }

    @Test
    fun unBogueNEnvoieNiLieuNiUrgence() {
        val bogue = SaisieAffaire(categorie = "bug", urgente = true, perimetre = "bat-a").aEnvoyer
        assertNull(bogue.perimetre)
        assertFalse(bogue.urgente)
        assertEquals("bat-a", SaisieAffaire(categorie = "panne", perimetre = "bat-a").aEnvoyer.perimetre)
    }

    @Test
    fun uneSuiteSansChangementDEtatEstUnCommentaireQuiDemandeSonTexte() {
        val garder = SaisieSuite("ouvert")
        assertFalse(garder.prete)
        assertEquals("commentaire", garder.copy(contenu = " Vu ").type)
        assertTrue(garder.copy(contenu = " Vu ").prete)
        val changer = SaisieSuite("ouvert", statut = "en_cours")
        assertTrue(changer.prete)
        assertEquals("etat", changer.type)
    }

    @Test
    fun lesGestesSuiventLesDroitsServis() {
        val tout = DroitsServisAffaire(repondre = true, changerEtat = true, archiver = true, corriger = true)
        assertEquals(GesteAffaire.entries, GesteAffaire.offerts(fiche(tout), conseil = false))
        // Le conseil suit par la Suite, il ne répond pas.
        assertFalse(GesteAffaire.Repondre in GesteAffaire.offerts(fiche(tout), conseil = true))
        // Ni réponse sur une actualité ni sur une affaire fusionnée ; pas d'archivage d'une archivée.
        assertFalse(GesteAffaire.Repondre in GesteAffaire.offerts(fiche(tout, categorie = "actualite"), conseil = false))
        assertFalse(GesteAffaire.Repondre in GesteAffaire.offerts(fiche(tout, fusionnee = true), conseil = false))
        assertFalse(GesteAffaire.Archiver in GesteAffaire.offerts(fiche(tout, archivee = true), conseil = false))
        assertEquals(listOf(GesteAffaire.Lien), GesteAffaire.offerts(fiche(DroitsServisAffaire()), conseil = false))
    }

    @Test
    fun lesEtatsDUneSuiteSontLesProposablesServis() {
        val r = ReferentielsAffaires(
            emptyList(),
            listOf(ReferentielsAffaires.Statut("ouvert", "Ouvert", false, "cs", true), ReferentielsAffaires.Statut("fermé", "Fermé", true, null, false)),
            emptyList(),
        )
        assertEquals(listOf("ouvert"), SaisieSuite.etats(fiche(DroitsServisAffaire()).copy(referentiels = r)).map { it.code })
        assertEquals(emptyList<String>(), SaisieSuite.etats(fiche(DroitsServisAffaire(), categorie = "actualite").copy(referentiels = r)).map { it.code })
    }

    @Test
    fun lesLieuxProposesSuiventLArbreServi() {
        val arbre = listOf(
            Perimetre("copro", "Toute la copropriété", parDefaut = true, niveauxLibres = 2),
            Perimetre("bat-a", "Bâtiment A", 1, parent = "copro", niveauxLibres = 0),
            Perimetre("toit-a", "Toit", 2, parent = "bat-a"),
            Perimetre("groupe", "Regroupement", selectionnable = false),
        )
        assertEquals(listOf("copro", "bat-a"), Perimetre.aSaisir(arbre).map { it.code })
        assertEquals(listOf("copro", "bat-a", "toit-a"), Perimetre.aSaisir(arbre, garder = "toit-a").map { it.code })
        assertEquals("copro", Perimetre.codeParDefaut(arbre))
    }

    @Test
    fun lesOptionsDuSiteSontGenerees() {
        assertEquals(listOf("epingle", "urgente"), TablesAffaires.options.map { it.cle })
        assertTrue(TablesAffaires.options.first().active(fiche(DroitsServisAffaire()).copy(epingle = true)))
    }
}
