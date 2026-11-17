// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.navigation

import androidx.compose.runtime.compositionLocalOf
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowLayoutInfo

/**
 * La posture de l'appareil (#242), pendant de `Posture` de l'iOS (coprofirst-ios#119) :
 * - `pliable` : l'appareil a une charnière (capteur d'angle) — plié comme déplié ; le marque-page
 *   n'est gardé que sur un tel appareil ;
 * - `pli` : un pli traverse la fenêtre, sa POSITION comprise (Jetpack WindowManager donne la
 *   géométrie, là où l'iOS n'a que l'état et l'angle) : la double page s'y coupe exactement.
 */
data class Posture(val pliable: Boolean = false, val pli: Pli? = null) {
    /** Un pli VERTICAL sépare deux pages : le carnet, en double page. */
    val enLivre: Boolean get() = pli?.vertical == true

    companion object {
        /**
         * Les plis que la fenêtre déclare : le premier. Un pliable sans espace entre ses écrans (un
         * seul écran souple) déplié à plat déclare un pli qui ne « sépare » pas — c'est pourtant le
         * carnet, comme le Duo de l'iOS : tout pli déclaré compte.
         */
        fun depuis(pliable: Boolean, plis: List<Pli>): Posture = Posture(pliable, plis.firstOrNull())
    }
}

/** Un pli de la fenêtre, en pixels de la fenêtre : de `gauche` à `droite` pour un pli vertical. */
data class Pli(val vertical: Boolean, val gauche: Int, val droite: Int, val aPlat: Boolean)

/** La posture que décrit WindowManager. */
fun WindowLayoutInfo.posture(pliable: Boolean): Posture = Posture.depuis(
    pliable,
    displayFeatures.filterIsInstance<FoldingFeature>().map {
        Pli(
            vertical = it.orientation == FoldingFeature.Orientation.VERTICAL,
            gauche = it.bounds.left,
            droite = it.bounds.right,
            aPlat = it.state == FoldingFeature.State.FLAT,
        )
    },
)

/** La posture de l'appareil ; hors de l'activité (tests, aperçus) : sans pli. */
val LocalPosture = compositionLocalOf { Posture() }
