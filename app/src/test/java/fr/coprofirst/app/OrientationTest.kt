// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app

import android.content.pm.ActivityInfo
import org.junit.Assert.assertEquals
import org.junit.Test

/** Portrait au téléphone, libre sur grand écran (#69), comme l'iOS. */
class OrientationTest {
    @Test
    fun unTelephoneResteEnPortrait() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, Orientation.voulue(360))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, Orientation.voulue(599))
    }

    @Test
    fun unGrandEcranTourneLibrement() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, Orientation.voulue(600))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, Orientation.voulue(840))
    }
}
