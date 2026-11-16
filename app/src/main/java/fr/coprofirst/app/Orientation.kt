// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app

import android.content.pm.ActivityInfo

/**
 * L'orientation de l'app, comme l'iOS (coprofirst-ios#220) : un téléphone reste en
 * portrait ; une tablette, un pliable déplié, une fenêtre de bureau tournent librement et se
 * redimensionnent (multi-fenêtre). Décidée à l'exécution, par la plus petite largeur de
 * l'écran — et non dans le manifeste : Android 16 ignore les verrous du manifeste sur grand
 * écran, mais une tablette plus ancienne les appliquerait (#69).
 */
object Orientation {
    /** La plus petite largeur à partir de laquelle l'écran est « grand » (`sw600dp`). */
    const val GRAND_ECRAN_DP = 600

    fun voulue(plusPetiteLargeurDp: Int): Int =
        if (plusPetiteLargeurDp < GRAND_ECRAN_DP) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
}
