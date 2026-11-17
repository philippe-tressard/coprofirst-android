// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.montre.tuile

import fr.coprofirst.app.fonctions.notifications.NotificationRecue
import fr.coprofirst.montre.cloche.Cloche
import fr.coprofirst.montre.liaison.EtatLecture
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class PhraseTuileTest {
    // Les pluriels français d'Android (`one` pour 0 et 1), rejoués ici sans ressources.
    private val libelles = LibellesTuile(
        nonReliee = "Ouvrez CoproFirst sur le téléphone",
        injoignable = "La résidence ne répond pas",
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

    @Test
    fun `la tuile compte la meme cloche en ligne et hors ligne`() {
        fun alerte(id: Int, urgente: Boolean) = NotificationRecue(id, "system", "t", "", false, urgente, Instant.EPOCH)
        val cloche = Cloche.de(listOf(alerte(1, true), alerte(2, false)))
        val adresse = "https://residence.exemple.test"
        assertEquals(EtatTuile.Compte(2, 1), EtatTuile.de(EtatLecture.Lue("Les Tilleuls", adresse, cloche)))
        assertEquals(EtatTuile.Compte(2, 1), EtatTuile.de(EtatLecture.HorsLigne("Les Tilleuls", adresse, cloche, Instant.EPOCH)))
        assertEquals(EtatTuile.Injoignable, EtatTuile.de(EtatLecture.Injoignable("Les Tilleuls", adresse)))
        assertEquals(EtatTuile.NonReliee, EtatTuile.de(EtatLecture.NonReliee))
        assertEquals("La résidence ne répond pas", PhraseTuile.de(EtatTuile.Injoignable, libelles))
    }
}
