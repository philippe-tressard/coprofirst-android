// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.entree

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.R
import fr.coprofirst.app.session.PreuveHote

/**
 * La confirmation de l'hôte, avant le premier mot de passe envoyé à un hôte jamais vu,
 * comme l'iOS (`ConfirmationHoteFeuille`) : l'hôte en entier, puis la preuve qu'il
 * sert. La règle vit dans `GardeHote` ; cette feuille la montre et transmet les accords.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeuilleConfirmationHote(confirmation: ConfirmationHote, parcours: ActionsEntree) {
    ModalBottomSheet(onDismissRequest = parcours::renoncerHote) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                stringResource(R.string.hote_titre),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            Text(stringResource(R.string.hote_annonce), style = MaterialTheme.typography.bodyMedium)
            // L'hôte en chasse fixe (« l » et « 1 », « O » et « 0 » s'y distinguent), coupé
            // au caractère, JAMAIS par une césure : un trait d'union ajouté se lirait comme
            // un tiret de l'hôte.
            Text(
                confirmation.installation.adresse?.enSaisie.orEmpty(),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    hyphens = Hyphens.None,
                    lineBreak = LineBreak.Simple,
                ),
            )
            Text(
                stringResource(R.string.hote_nom_servi, confirmation.installation.nom),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(R.string.hote_conseil),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Preuve(confirmation, parcours)
            Button(
                onClick = parcours::confirmerHote,
                enabled = confirmation.confirmable,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) { Text(stringResource(R.string.hote_confirmer)) }
            TextButton(onClick = parcours::renoncerHote, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.hote_annuler))
            }
        }
    }
}

@Composable
private fun Preuve(confirmation: ConfirmationHote, parcours: ActionsEntree) {
    when (val preuve = confirmation.preuve) {
        null -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Text(stringResource(R.string.hote_verification), style = MaterialTheme.typography.bodyMedium)
        }
        PreuveHote.Declaree -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.hote_reconnue), style = MaterialTheme.typography.bodyMedium)
        }
        else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Text(
                    stringResource(R.string.hote_avertissement),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Text(stringResource(R.string.hote_detail_avertissement), style = MaterialTheme.typography.bodyMedium)
            check(!preuve.reconnue)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(value = confirmation.secondAccord, role = Role.Checkbox, onValueChange = { parcours.basculerSecondAccord() }),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = confirmation.secondAccord, onCheckedChange = null)
                Text(stringResource(R.string.hote_second_accord), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
