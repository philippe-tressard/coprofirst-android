// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.montre

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import fr.coprofirst.montre.cloche.LectureCloche

/** L'unique activité de la montre. */
class ActiviteMontre : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // La liaison est éteinte (`LiaisonMontre`) : aucune résidence gardée à relire encore.
        val etat = EtatMontre.auLancement(residenceGardee = null)
        setContent {
            MaterialTheme {
                // La cloche se lit par le jeton de montre (lot 2) : tant qu'il n'y en a pas, rien.
                AppScaffold { EcranMontre(etat, LectureCloche.EnCours) }
            }
        }
    }
}
