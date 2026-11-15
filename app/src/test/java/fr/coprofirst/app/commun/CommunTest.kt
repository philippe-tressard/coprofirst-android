// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Statuts et rôles (tables générées) ; thème de l'appareil. */
class CommunTest {
    @Test
    fun libellesDeStatutEtDeRoleCommeLiOS() {
        assertEquals(LibelleTeinte("Locataire", "badge-purple"), Roles.statut("locataire"))
        assertEquals("Conseil syndical", Roles.role("conseil_syndical").libelle)
        // Un libellé hérité, puis l'autre table, puis la clé brute, en gris.
        assertEquals(LibelleTeinte.brut("Copropriétaire bailleur"), Roles.statut("bailleur"))
        assertEquals("Admin", Roles.statut("admin").libelle)
        assertEquals(LibelleTeinte.brut("statut_inconnu"), Roles.statut("statut_inconnu"))
        assertEquals("Copro. résident", Roles.statutAbrege("copropriétaire_résident"))
    }

    private class EnMemoire(var valeur: String? = null) : StockageApparence {
        override fun lire() = valeur
        override fun ecrire(valeur: String) { this.valeur = valeur }
    }

    @Test
    fun themeDeLAppareilGardeEtApplique() {
        val stockage = EnMemoire()
        val apparence = Apparence(stockage)
        assertEquals(TablesCommunes.THEME_PAR_DEFAUT, apparence.choix.value)
        assertTrue(apparence.sombre("auto", systemeSombre = true))
        apparence.choisir(Apparence.CLAIR)
        assertEquals(Apparence.CLAIR, stockage.valeur)
        assertFalse(apparence.sombre(apparence.choix.value, systemeSombre = true))
        assertEquals(Apparence.CLAIR, Apparence(stockage).choix.value)
        // Une valeur inconnue (stockage abîmé, option retirée) retombe sur le défaut.
        assertEquals(TablesCommunes.THEME_PAR_DEFAUT, Apparence(EnMemoire("violet")).choix.value)
        assertEquals(listOf("auto", "clair", "sombre"), apparence.options.map { it.valeur })
    }
}
