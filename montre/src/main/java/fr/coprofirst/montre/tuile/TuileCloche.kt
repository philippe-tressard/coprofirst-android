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
import fr.coprofirst.montre.liaison.LectureMontre
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * La tuile de la montre, pendant du widget de l'Apple Watch : une phrase, « 2 nouvelles
 * alertes · dont 1 urgente », et rien d'autre (`PhraseTuile`). Un toucher ouvre la cloche.
 *
 * Elle lit la cloche comme l'app (`LectureMontre`) : en ligne, ou la dernière lecture gardée
 * hors ligne. Montre pas reliée (ou liaison éteinte, `LiaisonMontre`), elle dit de la relier.
 */
class TuileCloche : TileService() {
    private val portee = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onTileRequest(requete: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        CallbackToFutureAdapter.getFuture { attente ->
            val travail = portee.launch {
                val etat = EtatTuile.de(LectureMontre.de(this@TuileCloche).lire())
                val mise = MiseEnPageTuile.de(this@TuileCloche, requete.deviceConfiguration, etat)
                attente.set(
                    TileBuilders.Tile.Builder()
                        .setResourcesVersion(VERSION_RESSOURCES)
                        .setFreshnessIntervalMillis(FRAICHEUR_MS)
                        .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(mise))
                        .build(),
                )
            }
            attente.addCancellationListener({ travail.cancel() }, Runnable::run)
            "tuile de la cloche"
        }

    override fun onTileResourcesRequest(requete: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        CallbackToFutureAdapter.getFuture { attente ->
            attente.set(ressources())
            "ressources de la tuile"
        }

    override fun onDestroy() {
        portee.cancel()
        super.onDestroy()
    }

    companion object {
        const val VERSION_RESSOURCES = "1"

        /** Le système redemande la tuile au plus tôt après ce délai (et à chaque lecture de l'app). */
        private const val FRAICHEUR_MS = 15 * 60 * 1000L

        /** Aucune image dans la tuile : des ressources vides, à leur version. */
        fun ressources(): ResourceBuilders.Resources = ResourceBuilders.Resources.Builder().setVersion(VERSION_RESSOURCES).build()
    }
}
