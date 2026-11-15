// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs

/** La hauteur des boutons d'action : au-delà des 48 dp tactiles, confortable au pouce. */
private val HAUTEUR_BOUTON = 52.dp

/**
 * Le bouton principal d'un écran, comme le `.plein` de l'iOS : fond `primaryPlein`,
 * texte `surPlein`. Pendant `enCours`, l'attente s'affiche à côté du libellé, et le
 * bouton garde sa taille. Le SEUL `Button` de Material de l'app (verifier-depot.sh).
 */
@Composable
fun BoutonPlein(
    @StringRes libelle: Int,
    modifier: Modifier = Modifier,
    actif: Boolean = true,
    enCours: Boolean = false,
    onClick: () -> Unit,
) {
    val couleurs = LocalCouleurs.current
    Button(
        onClick = onClick,
        enabled = actif && !enCours,
        colors = ButtonDefaults.buttonColors(containerColor = couleurs.primaryPlein, contentColor = couleurs.surPlein),
        modifier = modifier.fillMaxWidth().heightIn(min = HAUTEUR_BOUTON),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (enCours) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = couleurs.surPlein)
            Text(stringResource(libelle))
        }
    }
}

/** Le bouton secondaire (`.contour` de l'iOS), même gabarit que le principal. */
@Composable
fun BoutonContour(@StringRes libelle: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier.fillMaxWidth().heightIn(min = HAUTEUR_BOUTON)) {
        Text(stringResource(libelle))
    }
}
