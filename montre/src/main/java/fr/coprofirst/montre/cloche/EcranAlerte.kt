// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.montre.cloche

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import fr.coprofirst.app.commun.formats.FormatsDuSite
import fr.coprofirst.app.fonctions.notifications.NotificationRecue

/**
 * Le détail d'une alerte : son titre et sa date.
 *
 * À VENIR, déclaré (#207) : le `corps` en entier. C'est le HTML de l'éditeur du site, et
 * il se rend par le lecteur du téléphone (`LectureHTML`, `RenduHTML`), que la couche
 * commune partage (#206). « Continuer sur le téléphone » vient avec la liaison (lot 2).
 */
@Composable
fun EcranAlerte(alerte: NotificationRecue) {
    val etat = rememberTransformingLazyColumnState()
    ScreenScaffold(scrollState = etat) { marges ->
        TransformingLazyColumn(state = etat, contentPadding = marges, modifier = Modifier.testTag("montre.detail.${alerte.id}")) {
            item { ListHeader { Text(alerte.titre, style = MaterialTheme.typography.titleMedium) } }
            item { Text(FormatsDuSite.dateHeure(alerte.creeLe), style = MaterialTheme.typography.bodySmall) }
        }
    }
}
