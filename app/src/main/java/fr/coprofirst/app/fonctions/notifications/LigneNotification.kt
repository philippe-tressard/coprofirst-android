// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.notifications

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Drafts
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.commun.Carte
import fr.coprofirst.app.commun.PastilleTeinte
import fr.coprofirst.app.commun.formats.FormatsDuSite
import kotlinx.coroutines.launch
import java.time.Instant

/** Ce qu'une ligne sait faire ; `null` quand le geste n'est pas offert. */
class GestesLigne(
    val marquerLue: (() -> Unit)?,
    val supprimer: (() -> Unit)?,
)

/**
 * Une alerte dans la liste : hors édition, un toucher marque lu, un balayage vers la droite
 * aussi, vers la gauche il demande la suppression — et le lecteur d'écran trouve les deux
 * en actions. En édition, un toucher choisit.
 */
@Composable
fun LigneNotification(notification: NotificationRecue, gestes: GestesLigne?, choisie: Boolean?, choisir: () -> Unit) {
    val marquer = stringResource(R.string.notifications_marquer_lu)
    val supprimer = stringResource(R.string.notifications_supprimer)
    if (gestes == null) {
        CarteNotification(notification, choisie, choisir)
        return
    }
    val etat = rememberSwipeToDismissBoxState()
    val portee = rememberCoroutineScope()
    SwipeToDismissBox(
        state = etat,
        enableDismissFromStartToEnd = gestes.marquerLue != null,
        enableDismissFromEndToStart = gestes.supprimer != null,
        onDismiss = { sens ->
            when (sens) {
                SwipeToDismissBoxValue.StartToEnd -> gestes.marquerLue?.invoke()
                SwipeToDismissBoxValue.EndToStart -> gestes.supprimer?.invoke()
                SwipeToDismissBoxValue.Settled -> Unit
            }
            // La carte revient en place : la ligne part (ou non) avec la liste, pas avec le geste.
            portee.launch { etat.reset() }
        },
        backgroundContent = { FondDuBalayage(etat.dismissDirection) },
        modifier = Modifier.semantics {
            customActions = listOfNotNull(
                gestes.marquerLue?.let { f -> CustomAccessibilityAction(marquer) { f(); true } },
                gestes.supprimer?.let { f -> CustomAccessibilityAction(supprimer) { f(); true } },
            )
        },
    ) {
        CarteNotification(notification, null, gestes.marquerLue ?: {})
    }
}

/** Sous la carte balayée : ce que le geste va faire, dans sa couleur. */
@Composable
private fun FondDuBalayage(sens: SwipeToDismissBoxValue) {
    val couleurs = LocalCouleurs.current
    val fond = when (sens) {
        SwipeToDismissBoxValue.StartToEnd ->
            FondGeste(couleurs.primaryPlein, Icons.Outlined.Drafts, R.string.notifications_marquer_lu, Alignment.CenterStart)
        SwipeToDismissBoxValue.EndToStart ->
            FondGeste(couleurs.dangerPlein, Icons.Outlined.Delete, R.string.notifications_supprimer, Alignment.CenterEnd)
        SwipeToDismissBoxValue.Settled -> return
    }
    Box(
        Modifier.fillMaxSize().background(fond.couleur, RoundedCornerShape(Mise.rayon)).padding(horizontal = Mise.marge),
        contentAlignment = fond.alignement,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(fond.icone, contentDescription = null, tint = couleurs.surPlein)
            Text(stringResource(fond.libelle), color = couleurs.surPlein, fontWeight = FontWeight.SemiBold)
        }
    }
}

private class FondGeste(val couleur: Color, val icone: ImageVector, @param:StringRes val libelle: Int, val alignement: Alignment)

/** La carte d'une alerte : le point des non lues, le titre, « Urgent », l'heure et le texte. */
@Composable
fun CarteNotification(notification: NotificationRecue, choisie: Boolean?, toucher: () -> Unit) {
    val couleurs = LocalCouleurs.current
    val nonLue = stringResource(R.string.notifications_non_lue)
    val action = when (choisie) {
        null -> if (notification.lue) null else stringResource(R.string.notifications_marquer_lu)
        true -> stringResource(R.string.notifications_deselectionner)
        false -> stringResource(R.string.notifications_selectionner)
    }
    Carte(
        modifier = Modifier
            .testTag("notification.${notification.id}")
            .semantics(mergeDescendants = true) { if (!notification.lue) stateDescription = nonLue },
        ouvrir = if (action != null) toucher else null,
        action = action,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.alpha(if (notification.lue) 0.7f else 1f)) {
            if (choisie != null) {
                Checkbox(checked = choisie, onCheckedChange = null)
            } else {
                Box(
                    Modifier.padding(top = 7.dp).size(9.dp)
                        .background(if (notification.lue) Color.Transparent else couleurs.primary, CircleShape),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        notification.titre,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (notification.lue) FontWeight.Normal else FontWeight.SemiBold,
                        color = couleurs.text,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        dateDeLigne(notification.creeLe),
                        style = MaterialTheme.typography.labelSmall,
                        color = couleurs.textMuted,
                    )
                }
                if (notification.urgente) {
                    PastilleTeinte(stringResource(R.string.notifications_urgent), "badge-red", compacte = true)
                }
                val corps = remember(notification.corps) { texteDuCorps(notification.corps) }
                if (corps.isNotEmpty()) {
                    Text(corps, style = MaterialTheme.typography.bodyMedium, color = couleurs.textMuted)
                }
            }
        }
    }
}

/** Dans les 24 heures, l'heure seule ; au-delà, la date courte — comme le site. */
fun dateDeLigne(creeLe: Instant, maintenant: Instant = Instant.now()): String =
    if (creeLe.isAfter(maintenant.minus(EtatNotifications.VINGT_QUATRE_HEURES))) FormatsDuSite.heure(creeLe)
    else FormatsDuSite.dateCourte(creeLe)

/**
 * Le corps, du HTML de l'éditeur du site, en texte seul. ⚠️ Divergence déclarée : l'iOS le
 * rend riche (`TexteDuSite`, liste blanche, liens) ; le texte du site arrive dans la couche
 * commune avec 15b (#15), et ce rendu s'y remplacera.
 */
private fun texteDuCorps(html: String): String =
    HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()
