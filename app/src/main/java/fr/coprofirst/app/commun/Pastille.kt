// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.commun.formats.FormatsDuSite

/**
 * Une pastille de filtre ou d'onglet, comme `Pastille` de l'iOS : retenue, elle prend le
 * fond plein (`primaryPlein`, texte `surPlein`) ; sinon la surface et son filet. Le
 * `compte` ne s'affiche que sur la pastille retenue. Le lecteur d'écran la dit choisie.
 * `role` : `Role.Tab` dans une barre d'onglets (`BarreOnglets`), un bouton sinon.
 */
@Composable
fun Pastille(
    libelle: String,
    retenue: Boolean,
    modifier: Modifier = Modifier,
    compte: Int? = null,
    role: Role = Role.Button,
    onClick: () -> Unit,
) {
    val couleurs = LocalCouleurs.current
    val texte = if (retenue) couleurs.surPlein else couleurs.text
    Row(
        modifier
            .heightIn(min = Mise.cible)
            .clip(CircleShape)
            .background(if (retenue) couleurs.primaryPlein else couleurs.surface)
            .then(if (retenue) Modifier else Modifier.border(1.dp, couleurs.border, CircleShape))
            .selectable(selected = retenue, role = role, onClick = onClick)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val style = MaterialTheme.typography.labelLarge
        Text(libelle, style = style, fontWeight = FontWeight.Medium, color = texte, maxLines = 1)
        if (retenue && compte != null) {
            Text(
                FormatsDuSite.nombre(compte),
                style = style.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.Bold,
                color = texte,
            )
        }
    }
}
