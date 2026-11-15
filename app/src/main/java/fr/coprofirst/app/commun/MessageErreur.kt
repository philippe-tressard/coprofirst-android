// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.session.ErreurService

/** La phrase d'une erreur : celle du serveur s'il en a donné une, sinon celle de l'app. */
@Composable
fun ErreurService.phrase(): String = (this as? ErreurService.Refus)?.phraseServie ?: stringResource(phraseRes)

/**
 * Une erreur dite en une phrase, avec, au besoin, de quoi réessayer — comme l'iOS
 * (`MessageErreur`) : pastille d'erreur sur `dangerFond`, annoncée au lecteur d'écran.
 */
@Composable
fun MessageErreur(erreur: ErreurService, modifier: Modifier = Modifier, reessayer: (() -> Unit)? = null) {
    val couleurs = LocalCouleurs.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(couleurs.dangerFond, RoundedCornerShape(Mise.rayon))
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Error, contentDescription = null, tint = couleurs.danger)
        Text(erreur.phrase(), style = MaterialTheme.typography.bodyMedium, color = couleurs.text, modifier = Modifier.weight(1f))
        if (reessayer != null) {
            TextButton(onClick = reessayer, modifier = Modifier.heightIn(min = Mise.cible)) {
                Text(stringResource(R.string.reessayer), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
