// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.prestataires

import fr.coprofirst.api.modeles.NotationCreate
import fr.coprofirst.app.commun.LibelleTeinte
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Les états d'un avis et de la moyenne, lus dans la table du site (`TablesNotations`, #229). */
class EtatAvisTest {
    @Test
    fun chaqueEtatSeDitParLeSite() {
        SaisieAvis.etats.forEach { assertNotEquals("le libellé du site, pas le code", it, EtatAvis.teinte(it).libelle) }
        assertEquals(LibelleTeinte("Excellent", "badge-green"), EtatAvis.teinte("excellent"))
        assertEquals(LibelleTeinte.brut("inconnu"), EtatAvis.teinte("inconnu"))
    }

    @Test
    fun laMoyenneSuitLesSeuilsDuSite() {
        assertEquals("mauvais", EtatAvis.deMoyenne(2.49))
        assertEquals("neutre", EtatAvis.deMoyenne(2.5))
        assertEquals("neutre", EtatAvis.deMoyenne(3.99))
        assertEquals("excellent", EtatAvis.deMoyenne(4.0))
        assertEquals("excellent", EtatAvis.deMoyenne(4.33))
    }

    /** Un état que le site propose doit partir : le contrat l'accepte, et « mauvais » en est. */
    @Test
    fun lesEtatsDuSiteSontCeuxDuContrat() {
        val contrat = NotationCreate.Etat.entries.map { it.value }.toSet()
        assertTrue(TablesNotations.etats.toString(), contrat.containsAll(TablesNotations.etats))
        assertTrue(SaisieAvis.ETAT_MAUVAIS in TablesNotations.etats)
    }
}
