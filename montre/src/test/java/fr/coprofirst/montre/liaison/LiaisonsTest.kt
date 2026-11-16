// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.montre.liaison

import fr.coprofirst.app.fonctions.notifications.montre.MessageLiaison
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class LiaisonsTest {
    private class Coffre : CoffreLiaison {
        var gardee: LiaisonGardee? = null
        override fun lire() = gardee
        override fun garder(liaison: LiaisonGardee) { gardee = liaison }
        override fun effacer() { gardee = null }
    }

    private val coffre = Coffre()
    private val adresse = "https://residence.exemple.test"
    private fun liaison(adresse: String = this.adresse, expire: String = "2026-12-17T10:00:00Z") =
        MessageLiaison.Liaison(adresse, "Les Tilleuls", "jeton-de-montre", expire)

    @Test
    fun `la liaison du telephone se garde`() {
        Liaisons.recevoir(liaison(), coffre)
        val gardee = coffre.gardee!!
        assertEquals(adresse, gardee.adresse)
        assertEquals("Les Tilleuls", gardee.residence)
        assertEquals("jeton-de-montre", gardee.jeton)
        assertEquals(Instant.parse("2026-12-17T10:00:00Z"), gardee.expireLe)
    }

    @Test
    fun `une adresse qui n'est pas celle d'une installation ne se garde pas`() {
        Liaisons.recevoir(liaison("http://residence.exemple.test"), coffre)
        Liaisons.recevoir(liaison("https://residence.exemple.test/chemin"), coffre)
        Liaisons.recevoir(liaison("residence"), coffre)
        assertNull(coffre.gardee)
    }

    @Test
    fun `une echeance illisible ne se garde pas`() {
        Liaisons.recevoir(liaison(expire = "demain"), coffre)
        assertNull(coffre.gardee)
    }

    @Test
    fun `l'effacement ne vise que la residence gardee`() {
        Liaisons.recevoir(liaison(), coffre)
        Liaisons.recevoir(MessageLiaison.Effacement("https://autre.exemple.test"), coffre)
        assertEquals(adresse, coffre.gardee?.adresse)
        Liaisons.recevoir(MessageLiaison.Effacement(adresse), coffre)
        assertNull(coffre.gardee)
    }

    @Test
    fun `un jeton echu ne vaut plus`() {
        Liaisons.recevoir(liaison(), coffre)
        assertEquals(adresse, Liaisons.valide(coffre, Instant.parse("2026-12-17T09:59:59Z"))?.adresse)
        assertNull(Liaisons.valide(coffre, Instant.parse("2026-12-17T10:00:00Z")))
    }

    @Test
    fun `le jeton n'apparait dans aucune trace`() {
        Liaisons.recevoir(liaison(), coffre)
        assertEquals(false, coffre.gardee.toString().contains("jeton-de-montre"))
    }
}
