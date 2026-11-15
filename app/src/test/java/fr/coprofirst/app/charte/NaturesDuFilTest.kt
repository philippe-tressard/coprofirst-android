// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.charte

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Les natures du fil, générées : chacune a son pictogramme, et ses teintes sont celles de la palette. */
class NaturesDuFilTest {
    @Test
    fun chaqueIconeDeNatureASonPendant() {
        val icones = NaturesDuFil.parType.values.map { it.icone } + NaturesAffaire.toutes.map { it.icone } + NatureDuFil.inconnue.icone
        icones.forEach { assertTrue("icône de nature sans pendant : $it", it in Pictogrammes.parNom) }
    }

    @Test
    fun lesTeintesSontLesJetonsDeLaPalette() {
        val couleurs = Palettes.pour(null).clair
        val affaire = NatureDuFil.de("ticket_ouvert")
        assertEquals(couleurs.filRouge, affaire.couleur(couleurs))
        assertEquals(couleurs.filRougeFond, affaire.fond(couleurs))
        assertEquals(couleurs.primary, NatureDuFil.de("publication").couleur(couleurs))
        // Au sombre, la même nature prend la teinte sombre du même jeton.
        val sombre = Palettes.pour(null).sombre
        assertEquals(sombre.filRouge, affaire.couleur(sombre))
    }

    @Test
    fun unTypeInconnuEstNeutre() {
        assertEquals(NatureDuFil.inconnue, NatureDuFil.de("type_a_venir"))
        assertEquals(listOf("actualite", "calendrier", "activite"), NaturesAffaire.toutes.map { it.code })
    }
}
