// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.texte

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** La recherche d'une liste — le cas de l'iOS (`laRechercheCherchePartoutSansAccents`). */
class RechercheTest {
    @Test
    fun laRechercheCherchePartoutSansAccents() {
        assertTrue(Recherche.contientTousLesMots(listOf("4521", "Céleste Inventé", "Lot 12"), "celeste 12"))
        assertFalse(Recherche.contientTousLesMots(listOf("4521", "Alix Exemple", null), "celeste"))
        assertTrue(Recherche.contientTousLesMots(listOf("x"), "  "))
        assertTrue(Recherche.contientTousLesMots(listOf("ÉLAGAGE des arbres"), "arbres  ELAGAGE"))
    }

    @Test
    fun leRepliOteAccentsEtCasse() {
        assertEquals("ecole cote", Recherche.replier("École CÔTÉ"))
    }
}
