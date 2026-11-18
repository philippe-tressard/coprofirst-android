// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.session.audience

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import fr.coprofirst.app.Orientation

/**
 * Ce que la mesure d'audience lit de l'appareil pour en dire la CLASSE (#77, coprofirst#2774) :
 * des capacités que le système déclare, et la plus petite largeur de l'écran. Ni modèle, ni
 * version du système, ni identifiant — rien qui distingue un appareil d'un autre de sa classe.
 */
data class TraitsAppareil(
    val tv: Boolean = false,
    val montre: Boolean = false,
    val voiture: Boolean = false,
    val ordinateur: Boolean = false,
    /** `Configuration.smallestScreenWidthDp` : un pliable ouvert compte comme tablette, fermé comme téléphone. */
    val plusPetiteLargeurDp: Int,
) {
    /** La classe déclarée : les règles s'appliquent dans l'ordre, la première qui correspond l'emporte. */
    fun classe(): AppareilAudience = when {
        tv -> AppareilAudience.TV
        montre -> AppareilAudience.MONTRE
        voiture -> AppareilAudience.VOITURE
        ordinateur -> AppareilAudience.ORDINATEUR
        plusPetiteLargeurDp >= Orientation.GRAND_ECRAN_DP -> AppareilAudience.TABLETTE
        else -> AppareilAudience.TELEPHONE
    }

    companion object {
        /** Relu à chaque lot : un pliable change de classe en s'ouvrant. */
        fun de(contexte: Context): TraitsAppareil {
            val systeme = contexte.packageManager
            return TraitsAppareil(
                tv = systeme.hasSystemFeature(PackageManager.FEATURE_LEANBACK),
                montre = systeme.hasSystemFeature(PackageManager.FEATURE_WATCH),
                voiture = systeme.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE),
                // ChromeOS (Android 8.1 et plus) ; plus ancien, la capacité n'existe pas.
                ordinateur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 &&
                    systeme.hasSystemFeature(PackageManager.FEATURE_PC),
                plusPetiteLargeurDp = contexte.resources.configuration.smallestScreenWidthDp,
            )
        }
    }
}
