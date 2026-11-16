// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.montre.tuile

import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.types.layoutString
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import fr.coprofirst.montre.ActiviteMontre
import fr.coprofirst.montre.R

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
            attente.set(tuile(requete))
            "tuile de la cloche"
        }

    override fun onTileResourcesRequest(requete: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        CallbackToFutureAdapter.getFuture { attente ->
            attente.set(ResourceBuilders.Resources.Builder().setVersion(VERSION_RESSOURCES).build())
            "ressources de la tuile"
        }

    private fun tuile(requete: RequestBuilders.TileRequest): TileBuilders.Tile {
        // La dernière lecture gardée vient au lot 6 : aucune encore.
        val phrase = PhraseTuile.de(EtatTuile.de(cloche = null), libelles())
        val mise = materialScope(this, requete.deviceConfiguration) {
            primaryLayout(mainSlot = { text(phrase.layoutString, maxLines = 3) }, onClick = ouvrirLaCloche())
        }
        return TileBuilders.Tile.Builder()
            .setResourcesVersion(VERSION_RESSOURCES)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(mise))
            .build()
    }

    private fun libelles() = LibellesTuile(
        nonReliee = getString(R.string.montre_non_reliee),
        aucune = getString(R.string.montre_aucune_alerte),
        alertes = { n -> resources.getQuantityString(R.plurals.tuile_nouvelles_alertes, n, n) },
        dontUrgentes = { n -> resources.getQuantityString(R.plurals.tuile_dont_urgentes, n, n) },
        joindre = { a, b -> getString(R.string.tuile_alertes_dont, a, b) },
    )

    private fun ouvrirLaCloche(): ModifiersBuilders.Clickable =
        ModifiersBuilders.Clickable.Builder()
            .setId("ouvrir")
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(
                        ActionBuilders.AndroidActivity.Builder()
                            .setPackageName(packageName)
                            .setClassName(ActiviteMontre::class.java.name)
                            .build(),
                    )
                    .build(),
            )
            .build()

    private companion object {
        const val VERSION_RESSOURCES = "1"
    }
}
