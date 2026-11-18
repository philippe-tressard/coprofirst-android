// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.masquages

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import fr.coprofirst.app.R

/** Les repères du masquage, ceux de l'iOS. */
object ReperesMasquage {
    const val CONFIRMER = "masquage.confirmer"
    const val PROFIL = "profil.masquees"
    fun masque(auteurId: Int) = "masque.$auteurId"
    fun demasquer(auteurId: Int) = "masque.$auteurId.demasquer"
}

/**
 * « Masquer ce que publie… » : la question du site (`confirmerPuis` de `masquer()`), comme
 * l'iOS (`ConfirmationMasquage`) — elle dit ce qui disparaît et où revenir sur ce choix. La
 * boîte paraît tant que `auteur` n'est pas `null` ; « Masquer » la ferme et rend l'auteur à
 * l'écran, qui fait l'envoi, dit l'avis et relit sa liste (le filtre est au serveur).
 */
@Composable
fun ConfirmationMasquage(auteur: AuteurAMasquer?, masquer: (AuteurAMasquer) -> Unit, fermer: () -> Unit) {
    auteur ?: return
    AlertDialog(
        onDismissRequest = fermer,
        title = { Text(stringResource(R.string.masquage_question, auteur.nom)) },
        text = { Text(stringResource(R.string.masquage_message)) },
        confirmButton = {
            TextButton(
                onClick = {
                    fermer()
                    masquer(auteur)
                },
                modifier = Modifier.testTag(ReperesMasquage.CONFIRMER),
            ) { Text(stringResource(R.string.masquage_confirmer), fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = fermer) { Text(stringResource(R.string.annuler)) } },
    )
}
