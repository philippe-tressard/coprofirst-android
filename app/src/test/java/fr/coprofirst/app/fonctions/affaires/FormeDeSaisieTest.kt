// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.affaires

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** La saisie Essentielle ou Complète (#178), comme `FormeDeSaisie` de l'iOS : le serveur dit quoi, l'app range. */
class FormeDeSaisieTest {
    private fun categorie(code: String, vararg essentielles: String) =
        ReferentielsAffaires.Categorie(code, code, "", sectionsEssentielles = essentielles.toList())

    private val categories = listOf(
        categorie("panne", "titre", "description", "perimetre"),
        categorie("question", "titre", "description"),
    )

    @Test
    fun enEssentielleCeQuiNEstPasEssentielSeRangeDansLOrdreDuFormulaire() {
        val forme = FormeDeSaisie.de("essentielle", categories)!!
        assertTrue(forme.range)
        assertEquals(listOf(SectionAffaire.MiseEnAvant), forme.rangees("panne"))
        assertEquals(listOf(SectionAffaire.Perimetre, SectionAffaire.MiseEnAvant), forme.rangees("question"))
    }

    /** Sans catégorie choisie : ce que TOUTES partagent — la base du serveur, déduite, jamais écrite ici. */
    @Test
    fun sansCategorieCeQueToutesPartagent() {
        val forme = FormeDeSaisie.de("essentielle", categories)!!
        assertEquals(listOf(SectionAffaire.Perimetre, SectionAffaire.MiseEnAvant), forme.rangees(""))
    }

    /** Un bogue n'a ni lieu ni urgence : ce qui est éteint n'entre pas dans « Plus de détails ». */
    @Test
    fun uneSectionEteinteNeSeRangePas() {
        val forme = FormeDeSaisie.de("essentielle", categories)!!
        assertEquals(emptyList<SectionAffaire>(), forme.rangees("question", eteintes = setOf(SectionAffaire.Perimetre, SectionAffaire.MiseEnAvant)))
    }

    @Test
    fun enCompleteOuSansCategoriesServiesRienNeSeRange() {
        assertFalse(FormeDeSaisie.de("complete", categories)!!.range)
        assertEquals(emptyList<SectionAffaire>(), FormeDeSaisie.de("complete", categories)!!.rangees("question"))
        assertFalse("sans catégorie servie, rien ne le justifie", FormeDeSaisie.de("essentielle", emptyList())!!.range)
    }

    /** Un mot inconnu, ou un serveur qui ne sert pas la saisie : pas de forme, tout à son rang, sans phrase. */
    @Test
    fun unModeInconnuNeRangeRien() {
        assertNull(FormeDeSaisie.de(null, categories))
        assertNull(FormeDeSaisie.de("selon_role", categories))
    }
}
