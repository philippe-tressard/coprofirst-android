// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.montre.tuile

import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture

/**
 * La tuile de la montre, pendant du widget de l'Apple Watch : une phrase, « 2 nouvelles
 * alertes · dont 1 urgente », et rien d'autre (`PhraseTuile`). Un toucher ouvre la cloche.
 *
 * Elle ne lit pas le réseau : elle compte la DERNIÈRE lecture gardée de la cloche, que la
 * montre tient à jour (#207, lot 6). D'ici là, et tant que la liaison est éteinte
 * (`LiaisonMontre`), elle dit de relier la montre.
 */
class TuileCloche : TileService() {
    override fun onTileRequest(requete: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        CallbackToFutureAdapter.getFuture { attente ->
            // La dernière lecture gardée vient au lot 6 : aucune encore.
            val mise = MiseEnPageTuile.de(this, requete.deviceConfiguration, EtatTuile.de(cloche = null))
            attente.set(
                TileBuilders.Tile.Builder()
                    .setResourcesVersion(VERSION_RESSOURCES)
                    .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(mise))
                    .build(),
            )
            "tuile de la cloche"
        }

    override fun onTileResourcesRequest(requete: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        CallbackToFutureAdapter.getFuture { attente ->
            attente.set(ressources())
            "ressources de la tuile"
        }

    companion object {
        const val VERSION_RESSOURCES = "1"

        /** Aucune image dans la tuile : des ressources vides, à leur version. */
        fun ressources(): ResourceBuilders.Resources = ResourceBuilders.Resources.Builder().setVersion(VERSION_RESSOURCES).build()
    }
}
