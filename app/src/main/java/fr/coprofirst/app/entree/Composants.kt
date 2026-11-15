// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.entree

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.session.ErreurEntree

/** La hauteur des boutons d'action : au-delà des 48 dp tactiles, confortable au pouce. */
private val HAUTEUR_BOUTON = 52.dp

/** Le bouton principal d'un écran. Pendant `enCours`, il montre l'attente et ne répond plus. */
@Composable
fun BoutonPlein(@StringRes libelle: Int, enCours: Boolean = false, actif: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = actif && !enCours,
        modifier = Modifier.fillMaxWidth().heightIn(min = HAUTEUR_BOUTON),
    ) { Contenu(libelle, enCours) }
}

/** Le bouton secondaire, même gabarit que le principal. */
@Composable
fun BoutonContour(@StringRes libelle: Int, enCours: Boolean = false, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = !enCours,
        modifier = Modifier.fillMaxWidth().heightIn(min = HAUTEUR_BOUTON),
    ) { Contenu(libelle, enCours) }
}

/** Le libellé garde sa place pendant l'attente : le bouton ne change pas de taille. */
@Composable
private fun Contenu(@StringRes libelle: Int, enCours: Boolean) {
    Box(contentAlignment = Alignment.Center) {
        Text(
            stringResource(libelle),
            color = if (enCours) LocalContentColor.current.copy(alpha = 0f) else LocalContentColor.current,
        )
        if (enCours) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
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

/** Une rangée qui s'ouvre d'un toucher, avec ce qu'elle fait dit au lecteur d'écran. */
fun Modifier.cliquable(action: String, onClick: () -> Unit): Modifier =
    clickable(onClickLabel = action, role = Role.Button, onClick = onClick)
