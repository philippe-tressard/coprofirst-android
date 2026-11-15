// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.charte.Mise

/**
 * Une liste vide, dite — comme `ContentUnavailableView` de l'iOS : une icône, un titre,
 * une phrase qui dit quoi faire ou où cela se fait.
 */
@Composable
fun EtatVide(titre: String, modifier: Modifier = Modifier, icone: ImageVector? = null, explication: String? = null) {
    val couleurs = LocalCouleurs.current
    Column(
        modifier = modifier.fillMaxWidth().padding(Mise.marge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icone != null) Icon(icone, contentDescription = null, tint = couleurs.textMuted, modifier = Modifier.size(40.dp))
        Text(titre, style = MaterialTheme.typography.titleMedium, color = couleurs.text, textAlign = TextAlign.Center)
        if (explication != null) {
            Text(explication, style = MaterialTheme.typography.bodyMedium, color = couleurs.textMuted, textAlign = TextAlign.Center)
        }
    }
}
