// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.entree

import fr.coprofirst.app.navigation.Destination
import fr.coprofirst.app.session.Installation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** #280 : une notification touchée mène à la cloche de SA résidence, jamais à une résidence inconnue. */
class SollicitationTest {
    private val glycines = Installation("https://glycines.exemple.test", "Les Glycines")
    private val tilleuls = Installation("https://tilleuls.exemple.test", "Les Tilleuls")

    @Test
    fun uneNotificationOuvreLaClocheDeSaResidence() {
        val sollicitation = Sollicitation.deNotification(glycines.url, listOf(tilleuls, glycines))
        assertEquals(glycines.adresse, sollicitation?.adresse)
        assertEquals(LienEnAttente(glycines.adresse!!, Destination.Notifications), sollicitation?.enAttente)
    }

    @Test
    fun uneResidenceQueLAppareilNeConnaitPlusNOuvreRien() {
        assertNull(Sollicitation.deNotification(glycines.url, listOf(tilleuls)))
        assertNull(Sollicitation.deNotification("pas une adresse", listOf(glycines)))
    }
}
