// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.commun.cliquable

/**
 * En tête de l'écran : « Tout marquer lu » dès qu'une alerte attend (et leur nombre), ou,
 * en édition, « Tout sélectionner » ; « Modifier » / « OK » dès que la liste a une alerte.
 */
@Composable
fun BarreDesGestes(
    etat: EtatNotifications,
    enEdition: Boolean,
    toutesChoisies: Boolean,
    toutMarquerLu: () -> Unit,
    toutChoisir: (toutes: Boolean) -> Unit,
    basculer: () -> Unit,
) {
    val couleurs = LocalCouleurs.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = Mise.cible).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (enEdition) {
            TextButton(onClick = { toutChoisir(toutesChoisies) }, modifier = Modifier.testTag("notifications.toutSelectionner")) {
                Text(stringResource(if (toutesChoisies) R.string.notifications_tout_deselectionner else R.string.notifications_tout_selectionner))
            }
        } else if (etat.nonLues > 0) {
            TextButton(onClick = toutMarquerLu, modifier = Modifier.testTag("notifications.toutMarquerLu")) {
                Text(stringResource(R.string.notifications_tout_marquer_lu), fontWeight = FontWeight.SemiBold)
            }
            Text(TextesNotifications.nonLues(etat.nonLues), style = MaterialTheme.typography.labelMedium, color = couleurs.textMuted)
        }
        Spacer(Modifier.weight(1f))
        if (etat.liste.isNotEmpty()) {
            TextButton(onClick = basculer, modifier = Modifier.testTag("notifications.modifier")) {
                Text(
                    stringResource(if (enEdition) R.string.notifications_terminer else R.string.notifications_modifier),
                    fontWeight = if (enEdition) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

/**
 * « Supprimer les lues », le geste direct du site : toutes les lues du compte, après
 * confirmation. En tête de la liste — il défile avec elle, et un geste définitif ne se pose
 * pas sous le pouce. Discret comme le bouton du site (`btn-outline`) : la surface d'une
 * carte, le rouge dans le texte. Pendant une sélection, il attend.
 */
@Composable
fun GesteSurLesLues(actif: Boolean, demander: () -> Unit) {
    val couleurs = LocalCouleurs.current
    val forme = RoundedCornerShape(Mise.rayon)
    val libelle = stringResource(R.string.notifications_supprimer_lues)
    Row(
        modifier = Modifier
            .testTag("notifications.supprimerLues")
            .heightIn(min = Mise.cible)
            .clip(forme)
            .background(couleurs.surface)
            .border(1.dp, couleurs.border, forme)
            .then(if (actif) Modifier.cliquable(libelle, demander) else Modifier.alpha(0.45f))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Outlined.Delete, contentDescription = null, tint = couleurs.danger, modifier = Modifier.size(20.dp))
        Text(libelle, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = couleurs.danger)
    }
}

/**
 * En édition : supprimer la sélection, au bas de l'écran, au-dessus de la barre de la
 * résidence. ⚠️ Divergence déclarée : l'iOS en fait un bouton plein rouge ; la couche
 * commune n'a pas encore de bouton destructif plein (#42) — un bouton texte rouge d'ici là.
 */
@Composable
fun BarreDeSuppression(nombre: Int, demander: () -> Unit) {
    val couleurs = LocalCouleurs.current
    HorizontalDivider(color = couleurs.border)
    Row(
        Modifier.fillMaxWidth().background(couleurs.surface).padding(horizontal = Mise.marge, vertical = 4.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        TextButton(
            onClick = demander,
            enabled = nombre > 0,
            colors = ButtonDefaults.textButtonColors(contentColor = couleurs.danger),
            modifier = Modifier.heightIn(min = Mise.cible).testTag("notifications.supprimerSelection"),
        ) {
            Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(8.dp))
            Text(TextesNotifications.supprimerSelection(nombre), fontWeight = FontWeight.SemiBold)
        }
    }
}

/** UNE confirmation pour les trois suppressions (une, la sélection, les lues) : elle dit ce qui part. */
@Composable
fun ConfirmationSuppression(demande: SuppressionDemandee, annuler: () -> Unit, confirmer: () -> Unit) {
    val couleurs = LocalCouleurs.current
    AlertDialog(
        onDismissRequest = annuler,
        title = { Text(stringResource(R.string.notifications_confirmer_titre)) },
        text = { Text(TextesNotifications.message(demande)) },
        confirmButton = {
            TextButton(
                onClick = confirmer,
                colors = ButtonDefaults.textButtonColors(contentColor = couleurs.danger),
                modifier = Modifier.testTag("notifications.confirmerSuppression"),
            ) { Text(TextesNotifications.libelle(demande), fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            TextButton(onClick = annuler) { Text(stringResource(R.string.notifications_annuler)) }
        },
    )
}
