// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.prestataires

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Le commentaire d'un avis composé et relu comme le site (`phraseGeneree`,
 * `decomposerCommentaire`, #229) — les cas de `AvisSaisiTests` de l'iOS (coprofirst-ios#260).
 */
class CommentaireAvisTest {
    @Test
    fun laPhraseSuitLOrdreDuSiteEtSaForme() {
        assertEquals("", CommentaireAvis.phrase(emptySet(), emptySet()))
        assertEquals(
            "Points forts : délai respecté, travail soigné. Points faibles : retard.",
            CommentaireAvis.phrase(setOf("travail soigné", "délai respecté"), setOf("retard")),
        )
        assertEquals("Points faibles : retard, facture contestée.", CommentaireAvis.phrase(emptySet(), setOf("facture contestée", "retard")))
    }

    @Test
    fun leCommentaireEstLaPhrasePuisLeTexteLibre() {
        assertEquals("Points forts : délai respecté.\nTrès bien.", CommentaireAvis.complet("Points forts : délai respecté.", "  Très bien. "))
        assertEquals("Très bien.", CommentaireAvis.complet("", "Très bien."))
        assertEquals("", CommentaireAvis.complet("", "   "))
    }

    @Test
    fun uneLectureRendLesCasesEtLeTexte() {
        val phrase = CommentaireAvis.phrase(setOf("délai respecté"), setOf("retard", "intervenant injoignable"))
        val lu = CommentaireAvis.decomposer(CommentaireAvis.complet(phrase, "Deux relances.\nPuis réglé."))
        assertEquals(setOf("délai respecté"), lu.forts)
        assertEquals(setOf("retard", "intervenant injoignable"), lu.faibles)
        assertEquals("Deux relances.\nPuis réglé.", lu.libre)
    }

    @Test
    fun unTexteQuiRessembleALaPhraseResteLibre() {
        val inconnu = "Points forts : ponctuel.\nSuite."
        assertEquals(CommentaireAvis.Decompose(libre = inconnu), CommentaireAvis.decomposer(inconnu))
        assertEquals(CommentaireAvis.Decompose(libre = "Très bien."), CommentaireAvis.decomposer("Très bien."))
        assertEquals(CommentaireAvis.Decompose(libre = ""), CommentaireAvis.decomposer(null))
    }
}
