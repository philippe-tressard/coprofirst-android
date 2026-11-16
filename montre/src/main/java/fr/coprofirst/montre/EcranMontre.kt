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

/** L'écran de la montre selon son état. */
@Composable
fun EcranMontre(etat: EtatMontre) {
    ScreenScaffold {
        when (etat) {
            EtatMontre.NonReliee -> Message(stringResource(R.string.montre_non_reliee), "montre.nonReliee")
            // La cloche arrive avec les écrans (#207, lot 3) ; d'ici là, la liaison reste éteinte.
            is EtatMontre.Reliee -> Message(etat.residence, "montre.residence")
        }
    }
}

/** Un message seul, centré, dans le cercle du cadran. */
@Composable
private fun Message(texte: String, repere: String) {
    Box(Modifier.fillMaxSize().padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
        Text(
            texte,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag(repere),
        )
    }
}
