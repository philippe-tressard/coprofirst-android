// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.montre

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.wear.compose.material3.AppScaffold
import fr.coprofirst.app.session.ChiffrementKeystore
import fr.coprofirst.montre.liaison.CoffrePreferences

/** L'unique activité de la montre. */
class ActiviteMontre : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // La liaison que le téléphone a transmise, jeton chiffré au Keystore (#207, lot 2b).
        val coffre = CoffrePreferences(this, ChiffrementKeystore(CoffrePreferences.ALIAS))
        setContent {
            ThemeMontre {
                AppScaffold { PorteMontre(coffre) }
            }
        }
    }
}
