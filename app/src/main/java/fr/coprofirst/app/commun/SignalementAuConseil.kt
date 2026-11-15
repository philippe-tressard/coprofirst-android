// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.R

/** Les repères de la boîte (l'alerte de l'iOS n'en a pas : ses éléments se trouvent par leur texte). */
object ReperesSignalement {
    const val MOTIF = "signalement.motif"
    const val ENVOYER = "signalement.envoyer"
}

/**
 * « Signaler au conseil syndical », comme l'iOS (`SignalementAuConseil`) : un motif
 * OBLIGATOIRE, puis l'envoi — les mots de `signaler()` du site (`lib/signalements.ts`).
 * La boîte paraît tant que `cible` n'est pas `null` (une réponse, une idée, une affaire…) ;
 * « Signaler » rend la cible et le motif sans ses blancs, puis la boîte se ferme ; « Annuler »
 * la ferme sans rien envoyer. L'écran fait l'envoi (`ServiceSignalements`) et dit l'avis.
 */
@Composable
fun <C> SignalementAuConseil(cible: C?, envoyer: (C, String) -> Unit, fermer: () -> Unit) {
    cible ?: return
    var motif by rememberSaveable(cible) { mutableStateOf("") }
    val propre = motif.trim()
    AlertDialog(
        onDismissRequest = fermer,
        title = { Text(stringResource(R.string.signalement_titre)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.signalement_message))
                OutlinedTextField(
                    value = motif,
                    onValueChange = { motif = it },
                    placeholder = { Text(stringResource(R.string.signalement_motif)) },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth().testTag(ReperesSignalement.MOTIF),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    fermer()
                    envoyer(cible, propre)
                },
                enabled = propre.isNotEmpty(),
                modifier = Modifier.testTag(ReperesSignalement.ENVOYER),
            ) { Text(stringResource(R.string.signalement_signaler), fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = fermer) { Text(stringResource(R.string.annuler)) } },
    )
}
