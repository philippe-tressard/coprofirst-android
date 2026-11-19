// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import fr.coprofirst.app.commun.clavier.Raccourci
import fr.coprofirst.app.commun.clavier.RaccourciClavier

/**
 * Un écran qu'on tire pour actualiser, et que Ctrl+R actualise au clavier d'une tablette
 * (`.actualisable` de l'iOS, #69, coprofirst-ios#223) : le seul `PullToRefreshBox` de l'app —
 * `verifier-depot.sh` refuse tout autre, pour que le raccourci ne manque à aucun écran.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Actualisable(enCours: Boolean, actualiser: () -> Unit, modifier: Modifier = Modifier, contenu: @Composable BoxScope.() -> Unit) {
    RaccourciClavier(Raccourci.Actualiser, action = actualiser)
    PullToRefreshBox(isRefreshing = enCours, onRefresh = actualiser, modifier = modifier, content = contenu)
}
