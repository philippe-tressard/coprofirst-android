// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.notifications

import fr.coprofirst.app.charte.TracesDuSite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Les types d'alerte publiés (coprofirst#2802, #206) : chacun se dessine par les tracés du site. */
class TypesNotificationTest {
    @Test
    fun chaqueTypeEtLeDefautSeDessinent() {
        assertTrue(TypesNotification.parType.isNotEmpty())
        (TypesNotification.parType.values + TypesNotification.parDefaut).forEach { type ->
            assertNotNull("pictogramme hors du catalogue : ${type.pictogramme}", TracesDuSite.icone(type.pictogramme))
        }
    }

    @Test
    fun unTypeInconnuPrendLeDefaut() {
        assertEquals(TypesNotification.parDefaut, TypesNotification.de("inconnu"))
        assertEquals(TypesNotification.parDefaut, TypesNotification.de(null))
        assertEquals(TypesNotification.parType.getValue("sondage"), TypesNotification.de("sondage"))
    }
}
