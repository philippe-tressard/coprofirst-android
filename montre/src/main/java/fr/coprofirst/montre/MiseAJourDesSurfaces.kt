// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.montre

import android.content.ComponentName
import android.content.Context
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import fr.coprofirst.montre.complication.ComplicationCloche
import fr.coprofirst.montre.tuile.TuileCloche

/** Après une lecture de l'app : la tuile et la complication se relisent, pour dire la même chose. */
object MiseAJourDesSurfaces {
    fun demander(contexte: Context) {
        TileService.getUpdater(contexte).requestUpdate(TuileCloche::class.java)
        ComplicationDataSourceUpdateRequester.create(contexte, ComponentName(contexte, ComplicationCloche::class.java)).requestUpdateAll()
    }
}
