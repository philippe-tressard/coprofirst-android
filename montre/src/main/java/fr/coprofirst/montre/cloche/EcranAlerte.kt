// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.montre.cloche

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.OpenOnPhoneDialog
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.curvedText
import fr.coprofirst.app.commun.formats.FormatsDuSite
import fr.coprofirst.app.fonctions.notifications.NotificationRecue
import fr.coprofirst.montre.R
import fr.coprofirst.montre.telephone.OuvrirSurLeTelephone

/**
 * Le détail d'une alerte : son titre, sa date, son corps en entier (`CorpsDeLAlerte`, le
 * lecteur du site partagé avec le téléphone), et « Continuer sur le téléphone » quand elle
 * vise une page du site (`url`, déjà vérifiée par `LienSurLeTelephone`). Le toucher l'ouvre
 * sur le téléphone, puis la montre le confirme.
 */
@Composable
fun EcranAlerte(alerte: NotificationRecue, url: String?) {
    val etat = rememberTransformingLazyColumnState()
    val contexte = LocalContext.current
    var ouverte by rememberSaveable { mutableStateOf(false) }
    val confirmation = stringResource(R.string.montre_ouvert_sur_le_telephone)
    ScreenScaffold(scrollState = etat) { marges ->
        TransformingLazyColumn(state = etat, contentPadding = marges, modifier = Modifier.testTag("montre.detail.${alerte.id}")) {
            item { ListHeader { Text(alerte.titre, style = MaterialTheme.typography.titleMedium) } }
            item { Text(FormatsDuSite.dateHeure(alerte.creeLe), style = MaterialTheme.typography.bodySmall) }
            if (alerte.corps.isNotBlank()) {
                item { Text(CorpsDeLAlerte.de(alerte.corps), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("montre.corps")) }
            }
            if (url != null) {
                item {
                    FilledTonalButton(
                        onClick = {
                            OuvrirSurLeTelephone.ouvrir(contexte, url)
                            ouverte = true
                        },
                        label = { Text(stringResource(R.string.montre_continuer_sur_le_telephone)) },
                        modifier = Modifier.fillMaxWidth().testTag("montre.continuer"),
                    )
                }
            }
        }
    }
    OpenOnPhoneDialog(visible = ouverte, onDismissRequest = { ouverte = false }, curvedText = { curvedText(confirmation) })
}
