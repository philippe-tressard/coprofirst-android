// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.navigation

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.commun.cliquable

/**
 * « Plus », comme l'iOS (`PlusView`) : les pages au-delà de la barre, puis Profil et
 * À propos. « Se déconnecter » est au bas du Profil (#21), comme sur l'iOS.
 */
@Composable
fun ListeDePlus(pages: List<PageDeNavigation>, ouvrir: (String) -> Unit) {
    LazyColumn {
        items(PagesDePlus.suivies(pages), key = { it.id }) { page ->
            Entree(page.libelle, PagesDePlus.icone(page), "plus.${page.id}") { ouvrir(page.id) }
        }
    }
}

@Composable
private fun Entree(libelle: String, icone: ImageVector, repere: String, onClick: () -> Unit) {
    val couleurs = LocalCouleurs.current
    ListItem(
        headlineContent = { Text(libelle) },
        leadingContent = { Icon(icone, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = couleurs.surface),
        modifier = Modifier.cliquable(libelle, onClick).testTag(repere),
    )
}
