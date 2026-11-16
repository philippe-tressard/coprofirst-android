// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.audience

import fr.coprofirst.app.session.audience.CheminDuSite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Une fiche ouverte par la porte se mesure sous la page du site qui la montre (#144). */
class CheminsDeLAppTest {
    @Test
    fun uneAffaireEtUnSondageOntLeurPage() {
        assertEquals(CheminDuSite.affaire(12), CheminsDeLApp.fiche("affaire", "12"))
        assertEquals(CheminDuSite.sondage(3), CheminsDeLApp.fiche("sondage", "3"))
    }

    @Test
    fun uneAutreFicheOuUnIdentifiantIllisibleNeSeNotePas() {
        assertNull(CheminsDeLApp.fiche("composant-tiers", "OkHttp"))
        assertNull(CheminsDeLApp.fiche("affaire", "douze"))
        assertNull(CheminsDeLApp.fiche(null, null))
    }
}
