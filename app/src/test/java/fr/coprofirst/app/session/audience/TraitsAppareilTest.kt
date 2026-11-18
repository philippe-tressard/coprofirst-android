// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.session.audience

import org.junit.Assert.assertEquals
import org.junit.Test

/** #77 : la classe de l'appareil, dans l'ordre des règles — la première qui correspond l'emporte. */
class TraitsAppareilTest {
    private val telephone = TraitsAppareil(plusPetiteLargeurDp = 411)

    @Test
    fun chaqueRegleRendSaClasse() {
        assertEquals(AppareilAudience.TV, telephone.copy(tv = true).classe())
        assertEquals(AppareilAudience.MONTRE, telephone.copy(montre = true).classe())
        assertEquals(AppareilAudience.VOITURE, telephone.copy(voiture = true).classe())
        assertEquals(AppareilAudience.ORDINATEUR, telephone.copy(ordinateur = true).classe())
        assertEquals(AppareilAudience.TABLETTE, telephone.copy(plusPetiteLargeurDp = 600).classe())
        assertEquals(AppareilAudience.TELEPHONE, telephone.copy(plusPetiteLargeurDp = 599).classe())
    }

    @Test
    fun laPremiereRegleLEmporte() {
        val tout = TraitsAppareil(tv = true, montre = true, voiture = true, ordinateur = true, plusPetiteLargeurDp = 960)
        assertEquals(AppareilAudience.TV, tout.classe())
        assertEquals(AppareilAudience.MONTRE, tout.copy(tv = false).classe())
        assertEquals(AppareilAudience.VOITURE, tout.copy(tv = false, montre = false).classe())
        assertEquals(AppareilAudience.ORDINATEUR, tout.copy(tv = false, montre = false, voiture = false).classe())
    }

    /** Un pliable : ouvert, il compte comme tablette ; fermé, comme téléphone. */
    @Test
    fun unPliableChangeDeClasseEnSOuvrant() {
        assertEquals(AppareilAudience.TABLETTE, TraitsAppareil(plusPetiteLargeurDp = 673).classe())
        assertEquals(AppareilAudience.TELEPHONE, TraitsAppareil(plusPetiteLargeurDp = 344).classe())
    }
}
