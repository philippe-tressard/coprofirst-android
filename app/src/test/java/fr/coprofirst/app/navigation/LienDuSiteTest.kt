// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Le chemin d'un lien d'application → l'écran visé (#147), comme `LienUniversel` de l'iOS. */
class LienDuSiteTest {
    @Test
    fun laRacineEtUnCheminInconnuMenentAuFil() {
        assertEquals(LienDuSite.FIL, LienDuSite.destination("/"))
        assertEquals(LienDuSite.FIL, LienDuSite.destination(""))
        assertEquals(LienDuSite.FIL, LienDuSite.destination("/inconnu/ici"))
        assertEquals(LienDuSite.FIL, LienDuSite.destination("/tickets/douze"))
    }

    @Test
    fun uneAffaireOuvreSaFicheSansSonAncre() {
        assertEquals(Destination.Element("affaire", "12"), LienDuSite.destination("/tickets/12"))
        assertEquals(Destination.Element("affaire", "12"), LienDuSite.destination("/tickets/12#msg-45"))
    }

    @Test
    fun lesActualitesSontLesAffairesEtUneRouteSaPage() {
        assertEquals(Destination.Page("mes-demandes"), LienDuSite.destination("/actualites"))
        assertEquals(Destination.Page("mes-demandes"), LienDuSite.destination("/actualites#pub-3"))
        assertEquals(Destination.Page("mes-demandes"), LienDuSite.destination("/tickets"))
        assertEquals(Destination.Page("communaute"), LienDuSite.destination("/sondages?tri=recent"))
        assertEquals(Destination.Page("faq"), LienDuSite.destination("/faq/"))
    }

    @Test
    fun laConnexionEstLAffaireDeLEntree() {
        assertNull(LienDuSite.destination("/auth/connexion"))
    }
}
