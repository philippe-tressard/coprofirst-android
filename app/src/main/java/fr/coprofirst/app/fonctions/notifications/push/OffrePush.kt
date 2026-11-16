// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.notifications.push

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.commun.BoutonContour
import fr.coprofirst.app.commun.BoutonPlein
import fr.coprofirst.app.commun.Carte
import fr.coprofirst.app.commun.GRANDE_ECHELLE

/**
 * L'offre d'activer les notifications, en tête de la cloche — le moment où la personne
 * regarde ses alertes, jamais au lancement — comme l'iOS (`OffrePush`). L'autorisation du
 * système ne se demande qu'après « Activer » ; « Plus tard » tait l'offre quelques jours.
 * L'écran ne la montre que si `NotificationsPush.offreDue` : jamais tant que le relais
 * n'est pas actif (`RelaisPush`).
 */
@Composable
fun OffrePush(push: NotificationsPush, modifier: Modifier = Modifier) {
    val couleurs = LocalCouleurs.current
    val demande = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { accordee ->
        push.apresDemande(accordee)
    }
    val activer = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            demande.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // Avant Android 13, aucune alerte du système : le choix est celui de la personne.
            push.apresDemande(accordee = true)
        }
    }
    val grand = LocalDensity.current.fontScale >= GRANDE_ECHELLE
    Carte(modifier.testTag("push.offre")) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Outlined.NotificationsActive, contentDescription = null, tint = couleurs.primary, modifier = Modifier.size(28.dp))
            Text(
                stringResource(R.string.push_titre),
                style = MaterialTheme.typography.titleMedium,
                color = couleurs.text,
                modifier = Modifier.semantics { heading() },
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.push_explication), style = MaterialTheme.typography.bodyMedium, color = couleurs.textMuted)
        Spacer(Modifier.height(12.dp))
        if (grand) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BoutonPlein(R.string.push_activer, Modifier.testTag("push.activer"), onClick = activer)
                BoutonContour(R.string.push_plus_tard, Modifier.testTag("push.plusTard"), onClick = push::reporter)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BoutonPlein(R.string.push_activer, Modifier.weight(1f).testTag("push.activer"), onClick = activer)
                BoutonContour(R.string.push_plus_tard, Modifier.weight(1f).testTag("push.plusTard"), onClick = push::reporter)
            }
        }
    }
}
