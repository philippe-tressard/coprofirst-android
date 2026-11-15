// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.navigation

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Pictogrammes
import fr.coprofirst.app.commun.cliquable

/**
 * « Plus », comme l'iOS (`PlusView`) : les pages au-delà de la barre, puis Profil et
 * À propos. ⚠️ Divergence déclarée : « Se déconnecter » y est, en attendant l'écran
 * Profil (#21) — c'est là que l'iOS le place ; il y passera avec lui.
 */
@Composable
fun ListeDePlus(pages: List<PageDeNavigation>, ouvrir: (String) -> Unit, deconnecter: () -> Unit) {
    LazyColumn {
        items(pages, key = { it.id }) { page -> Entree(page.libelle, Pictogrammes.icone(page.icone), "plus.${page.id}") { ouvrir(page.id) } }
        item { Entree(PagesDePlus.profil.libelle, Icons.Filled.AccountCircle, "plus.profil") { ouvrir(PagesDePlus.profil.id) } }
        item { Entree(PagesDePlus.aPropos.libelle, Icons.Filled.Info, "plus.a-propos") { ouvrir(PagesDePlus.aPropos.id) } }
        item { HorizontalDivider() }
        item { Entree(stringResource(R.string.se_deconnecter), Icons.AutoMirrored.Filled.Logout, "plus.deconnexion", deconnecter) }
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
