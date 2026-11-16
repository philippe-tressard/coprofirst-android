// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.montre

import fr.coprofirst.app.fonctions.notifications.montre.LiaisonMontre
import org.junit.Assert.assertEquals
import org.junit.Test

class EtatMontreTest {
    private val tilleuls = EtatMontre.Reliee("https://residence.exemple.test", "Les Tilleuls")

    @Test
    fun `drapeau eteint - une liaison gardee ne s'ouvre pas`() {
        assertEquals(EtatMontre.NonReliee, EtatMontre.auLancement(tilleuls, actif = false))
    }

    @Test
    fun `drapeau allume - sans liaison gardee, la montre n'est pas reliee`() {
        assertEquals(EtatMontre.NonReliee, EtatMontre.auLancement(null, actif = true))
        assertEquals(EtatMontre.NonReliee, EtatMontre.auLancement(EtatMontre.Reliee("  ", "Les Tilleuls"), actif = true))
        assertEquals(EtatMontre.NonReliee, EtatMontre.auLancement(EtatMontre.Reliee("https://residence.exemple.test", " "), actif = true))
    }

    @Test
    fun `drapeau allume - la residence gardee s'ouvre`() {
        assertEquals(tilleuls, EtatMontre.auLancement(tilleuls, actif = true))
    }

    @Test
    fun `le drapeau est eteint tant que le jeton de montre n'est pas servi`() {
        // coprofirst#2771 et la décision du canal (#207) : l'allumer est un geste de PR, testé ici.
        assertEquals(false, LiaisonMontre.ACTIF)
    }
}
