// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.fil

import fr.coprofirst.app.commun.fil.JourDuFil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** #242 : le Fil d'un carnet tourne ses pages sur les jours d'ACTIVITÉ, du plus récent au plus ancien. */
class FeuilletsDuFilTest {
    private val jours = listOf("2026-11-17", "2026-11-15", "2026-11-10").map { JourDuFil(it, emptyList()) }

    @Test
    fun leJourOuvertEstCeluiDemandeSinonLePlusRecent() {
        assertEquals("2026-11-15", FeuilletsDuFil.jour(jours, "2026-11-15")?.jour)
        assertEquals("2026-11-17", FeuilletsDuFil.jour(jours, null)?.jour)
        // Un jour qui n'a plus de cartes (relu) : le plus récent.
        assertEquals("2026-11-17", FeuilletsDuFil.jour(jours, "2026-11-16")?.jour)
        assertNull(FeuilletsDuFil.jour(emptyList(), null))
    }

    @Test
    fun veilleEtLendemainSautentLesJoursSansActivite() {
        assertEquals("2026-11-15", FeuilletsDuFil.veille(jours, "2026-11-17"))
        assertEquals("2026-11-10", FeuilletsDuFil.veille(jours, "2026-11-15"))
        assertNull(FeuilletsDuFil.veille(jours, "2026-11-10"))
        assertEquals("2026-11-17", FeuilletsDuFil.lendemain(jours, "2026-11-15"))
        assertNull(FeuilletsDuFil.lendemain(jours, "2026-11-17"))
        assertNull(FeuilletsDuFil.lendemain(jours, "2026-11-16"))
    }
}
