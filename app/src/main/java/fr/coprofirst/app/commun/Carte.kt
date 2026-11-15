// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.charte.Mise

/**
 * La surface d'une carte, comme l'iOS (`Carte`) : `surface`, un filet `border`, ni ombre
 * ni dégradé. `ouvrir` la rend touchable (avec ce qu'elle fait dit au lecteur d'écran).
 */
@Composable
fun Carte(
    modifier: Modifier = Modifier,
    ouvrir: (() -> Unit)? = null,
    action: String? = null,
    contenu: @Composable ColumnScope.() -> Unit,
) {
    val couleurs = LocalCouleurs.current
    val forme = RoundedCornerShape(Mise.rayon)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(forme)
            .background(couleurs.surface)
            .border(1.dp, couleurs.border, forme)
            .then(if (ouvrir != null) Modifier.clickable(onClickLabel = action, role = Role.Button, onClick = ouvrir) else Modifier)
            .padding(16.dp),
        content = contenu,
    )
}
