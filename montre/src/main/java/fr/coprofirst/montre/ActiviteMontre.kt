// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.montre

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme

/** L'unique activité de la montre. */
class ActiviteMontre : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // La liaison est éteinte (`LiaisonMontre`) : aucune résidence gardée à relire encore.
        val etat = EtatMontre.auLancement(residenceGardee = null)
        setContent {
            MaterialTheme {
                AppScaffold { EcranMontre(etat) }
            }
        }
    }
}
