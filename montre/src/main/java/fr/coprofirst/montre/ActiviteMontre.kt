// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.montre

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.wear.compose.material3.AppScaffold
import fr.coprofirst.app.session.ChiffrementKeystore
import fr.coprofirst.montre.liaison.CoffrePreferences
import fr.coprofirst.montre.liaison.LectureMontre
import fr.coprofirst.montre.liaison.Liaisons

/** L'unique activité de la montre. */
class ActiviteMontre : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // La liaison que le téléphone a transmise (jeton chiffré au Keystore) et la lecture de la
        // cloche, avec sa dernière lecture gardée pour le hors-ligne (#207, lots 2b et 6).
        val coffre = CoffrePreferences(this, ChiffrementKeystore(CoffrePreferences.ALIAS))
        val lecture = LectureMontre.de(this)
        val depart = EtatMontre.auLancement(Liaisons.valide(coffre)?.let { EtatMontre.Reliee(it.adresse, it.residence) })
        setContent {
            ThemeMontre {
                AppScaffold {
                    PorteMontre(depart) {
                        lecture.lire().also { MiseAJourDesSurfaces.demander(this@ActiviteMontre) }
                    }
                }
            }
        }
    }
}
