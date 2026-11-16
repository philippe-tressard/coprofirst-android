// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.montre.tuile

import android.content.Context
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.types.layoutString
import fr.coprofirst.montre.ActiviteMontre
import fr.coprofirst.montre.R

/**
 * La mise en page de la tuile, écrite une fois : la tuile la sert (`TuileCloche`), et le test
 * d'interface la rend pour en tirer l'aperçu du manifeste (`ApercuTuileTest`).
 */
object MiseEnPageTuile {
    fun de(contexte: Context, appareil: DeviceParameters, etat: EtatTuile): LayoutElement {
        val phrase = PhraseTuile.de(etat, libelles(contexte))
        return materialScope(contexte, appareil) {
            primaryLayout(mainSlot = { text(phrase.layoutString, maxLines = 3) }, onClick = ouvrirLaCloche(contexte))
        }
    }

    fun libelles(contexte: Context) = LibellesTuile(
        nonReliee = contexte.getString(R.string.montre_non_reliee),
        aucune = contexte.getString(R.string.montre_aucune_alerte),
        alertes = { n -> contexte.resources.getQuantityString(R.plurals.tuile_nouvelles_alertes, n, n) },
        dontUrgentes = { n -> contexte.resources.getQuantityString(R.plurals.tuile_dont_urgentes, n, n) },
        joindre = { a, b -> contexte.getString(R.string.tuile_alertes_dont, a, b) },
    )

    private fun ouvrirLaCloche(contexte: Context): ModifiersBuilders.Clickable =
        ModifiersBuilders.Clickable.Builder()
            .setId("ouvrir")
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(
                        ActionBuilders.AndroidActivity.Builder()
                            .setPackageName(contexte.packageName)
                            .setClassName(ActiviteMontre::class.java.name)
                            .build(),
                    )
                    .build(),
            )
            .build()
}
