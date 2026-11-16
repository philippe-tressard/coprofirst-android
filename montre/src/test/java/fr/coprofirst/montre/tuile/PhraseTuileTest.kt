// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.montre.tuile

import fr.coprofirst.app.fonctions.notifications.NotificationRecue
import fr.coprofirst.montre.cloche.Cloche
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class PhraseTuileTest {
    // Les pluriels français d'Android (`one` pour 0 et 1), rejoués ici sans ressources.
    private val libelles = LibellesTuile(
        nonReliee = "Ouvrez CoproFirst sur le téléphone",
        aucune = "Aucune alerte",
        alertes = { n -> if (n <= 1) "$n nouvelle alerte" else "$n nouvelles alertes" },
        dontUrgentes = { n -> if (n <= 1) "dont $n urgente" else "dont $n urgentes" },
        // Le séparateur réel est une ressource, jugée par verifier_separateurs.py.
        joindre = { a, b -> "$a | $b" },
    )

    @Test
    fun `la phrase du widget de l'Apple Watch`() {
        assertEquals("2 nouvelles alertes | dont 1 urgente", PhraseTuile.de(EtatTuile.Compte(2, 1), libelles))
    }

    @Test
    fun `sans urgente, rien apres le nombre`() {
        assertEquals("1 nouvelle alerte", PhraseTuile.de(EtatTuile.Compte(1, 0), libelles))
    }

    @Test
    fun `aucune non lue`() {
        assertEquals("Aucune alerte", PhraseTuile.de(EtatTuile.Compte(0, 0), libelles))
    }

    @Test
    fun `non reliee`() {
        assertEquals("Ouvrez CoproFirst sur le téléphone", PhraseTuile.de(EtatTuile.NonReliee, libelles))
    }

    @Test
    fun `l'etat de la tuile se compte sur la cloche lue`() {
        fun alerte(id: Int, lue: Boolean, urgente: Boolean) = NotificationRecue(id, "system", "t", "", lue, urgente, Instant.EPOCH)
        val cloche = Cloche.de(listOf(alerte(1, false, true), alerte(2, false, false), alerte(3, true, true)))
        assertEquals(EtatTuile.Compte(nonLues = 2, urgentes = 1), EtatTuile.de(cloche))
        assertEquals(EtatTuile.NonReliee, EtatTuile.de(null))
    }
}
