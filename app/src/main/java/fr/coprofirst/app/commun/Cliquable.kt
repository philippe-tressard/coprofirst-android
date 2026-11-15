// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun

import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role

/** Une rangée qui s'ouvre d'un toucher, avec ce qu'elle fait dit au lecteur d'écran. */
fun Modifier.cliquable(action: String, onClick: () -> Unit): Modifier =
    clickable(onClickLabel = action, role = Role.Button, onClick = onClick)
