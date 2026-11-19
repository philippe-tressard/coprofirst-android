// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.fil

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** #295 : la carte du fil s'empile aux tailles d'accessibilité, et seulement là — comme l'iOS (#266). */
class CarteDuFilGrandTexteTest {
    @Test
    fun laCarteSEmpileAuTresGrandTexteSeulement() {
        assertFalse(empilee(1f))
        assertFalse(empilee(1.3f))
        assertTrue(empilee(1.5f))
        assertTrue(empilee(2f))
    }
}
