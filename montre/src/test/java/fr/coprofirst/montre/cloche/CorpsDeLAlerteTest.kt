// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.montre.cloche

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CorpsDeLAlerteTest {
    @Test
    fun `le texte du site se lit, ses liens ne se touchent pas`() {
        val corps = CorpsDeLAlerte.de("""<p>L'eau sera coupée <strong>jeudi</strong>.</p><p>Voir <a href="https://residence.exemple.test/tickets/12">l'affaire</a>.</p>""")
        assertTrue(corps.text, corps.text.contains("L'eau sera coupée jeudi."))
        assertTrue(corps.text, corps.text.contains("Voir l'affaire."))
        assertEquals(emptyList<Any>(), corps.getLinkAnnotations(0, corps.length))
    }

    @Test
    fun `un corps vide reste vide`() {
        assertEquals("", CorpsDeLAlerte.de("").text)
    }
}
