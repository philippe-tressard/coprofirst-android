// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.entree

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.session.ErreurEntree

/** La hauteur des boutons d'action : au-delà des 48 dp tactiles, confortable au pouce. */
private val HAUTEUR_BOUTON = 52.dp

/**
 * Le bouton principal d'un écran, comme le `.plein` de l'iOS : fond `primaryPlein`,
 * texte `surPlein`. Pendant `enCours`, l'attente s'affiche à côté du libellé, et le
 * bouton garde sa taille.
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

/** Le bouton secondaire, même gabarit que le principal. */
@Composable
fun BoutonContour(@StringRes libelle: Int, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = HAUTEUR_BOUTON)) {
        Text(stringResource(libelle))
    }
}

/** La phrase d'un échec, annoncée par le lecteur d'écran dès qu'elle paraît. */
@Composable
fun MessageErreur(erreur: ErreurEntree) {
    Text(
        stringResource(erreur.phrase),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}
