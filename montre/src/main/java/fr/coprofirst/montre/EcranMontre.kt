// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.montre

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import fr.coprofirst.montre.cloche.LectureCloche

/** L'écran de la montre selon son état : pas reliée, ou la cloche de la résidence reliée. */
@Composable
fun EcranMontre(etat: EtatMontre, lecture: LectureCloche) {
    when (etat) {
        EtatMontre.NonReliee -> NonReliee()
        is EtatMontre.Reliee -> NavigationMontre(etat.residence, lecture)
    }
}

/** « Ouvrez CoproFirst sur le téléphone pour relier la montre », centré dans le cadran. */
@Composable
private fun NonReliee() {
    ScreenScaffold {
        Box(Modifier.fillMaxSize().padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.montre_non_reliee),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("montre.nonReliee"),
            )
        }
    }
}
