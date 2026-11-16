// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.notifications.montre

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class MessageLiaisonTest {
    private val liaison = MessageLiaison.Liaison("https://residence.exemple.test", "Les Tilleuls", "jeton-de-montre", "2026-12-16T10:00:00Z")

    @Test
    fun `un message fait l'aller-retour`() {
        assertEquals(liaison, MessageLiaison.depuis(MessageLiaison.versDonnees(liaison)))
        val effacement = MessageLiaison.Effacement("https://residence.exemple.test")
        assertEquals(effacement, MessageLiaison.depuis(MessageLiaison.versDonnees(effacement)))
    }

    @Test
    fun `un message incomplet ou inconnu ne se lit pas`() {
        val donnees = MessageLiaison.versDonnees(liaison)
        for (cle in donnees.keys) assertNull(cle, MessageLiaison.depuis(donnees - cle))
        assertNull(MessageLiaison.depuis(donnees + ("jeton" to " ")))
        assertNull(MessageLiaison.depuis(donnees + ("geste" to "autre")))
    }

    @Test
    fun `le jeton n'apparait dans aucune trace`() {
        assertFalse(liaison.toString().contains("jeton-de-montre"))
    }
}
