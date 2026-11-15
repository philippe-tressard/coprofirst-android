// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * L'avis éphémère, comme l'iOS (`AvisEphemereTests`) : une phrase remplace la précédente,
 * son retrait n'efface jamais celle qui l'a remplacée, et celle d'une feuille attend sa
 * fermeture.
 */
class AvisDuGesteTest {
    @Test
    fun unAvisRemplacePuisRetireGardeLeSuivant() {
        val avis = AvisDuGeste()
        avis.dire("Signalement enregistré")
        val premiere = avis.phrase!!
        avis.dire("Accès enregistré")
        // La minuterie du premier avis arrive à son terme : elle ne retire que LUI.
        avis.retirer(premiere)
        assertEquals("Accès enregistré", avis.texte)
        avis.retirer(avis.phrase!!)
        assertNull(avis.texte)
    }

    @Test
    fun laMemePhraseRediteRepartDeZero() {
        val avis = AvisDuGeste()
        avis.dire("Accès enregistré")
        val premiere = avis.phrase!!
        avis.dire("Accès enregistré")
        assertNotEquals(premiere, avis.phrase)
        // La minuterie de la première ne retire pas la seconde, pourtant identique.
        avis.retirer(premiere)
        assertEquals("Accès enregistré", avis.texte)
    }

    @Test
    fun lAvisDUneFeuilleAttendSaFermeture() {
        val avis = AvisDuGeste()
        avis.direApresFermeture("Accès enregistré", TonAvis.Avertissement)
        assertNull("rien derrière la feuille", avis.texte)
        avis.feuilleFermee()
        assertEquals("dit une fois la feuille fermée", "Accès enregistré", avis.texte)
        assertEquals(TonAvis.Avertissement, avis.phrase?.ton)
        avis.retirer(avis.phrase!!)
        avis.feuilleFermee()
        assertNull("une feuille fermée sans geste ne redit rien", avis.texte)
    }

    @Test
    fun lesTonsDuSite() {
        assertEquals(TonAvis.Succes, AvisDuGeste().apply { dire("x") }.phrase?.ton)
        assertEquals(TonAvis.Erreur, AvisDuGeste().apply { dire(PhraseAvis("x", TonAvis.Erreur)) }.phrase?.ton)
        assertEquals(listOf("success", "warning", "error", "info"), TonAvis.entries.map { it.valeur })
        assertEquals(TonAvis.Avertissement, TonAvis.depuis("warning"))
        assertEquals(TonAvis.Info, TonAvis.depuis("inconnu"))
        assertEquals(TonAvis.Info, TonAvis.depuis(null))
    }

    @Test
    fun laDureeEstCelleDuSite() {
        // Générée depuis `dureeToastMs` de tables-clients.json (`DUREE_TOAST_MS` du site).
        assertEquals(4000L, AvisDuSite.DUREE_MS)
    }
}
