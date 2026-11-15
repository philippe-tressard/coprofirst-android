// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.charte

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Les icônes du site (noms Lucide) en icônes de Material, comme l'iOS les traduit en SF
 * Symbols (`Pictogrammes`). Un nom absent prend le repli — et un test refuse qu'une page
 * de navigation générée en manque.
 */
object Pictogrammes {
    val parNom: Map<String, ImageVector> = mapOf(
        "layout-dashboard" to Icons.Filled.Dashboard,
        "building-2" to Icons.Filled.Apartment,
        "message-square-text" to Icons.Filled.Forum,
        "key-round" to Icons.Filled.Key,
        "users" to Icons.Filled.People,
        "hard-hat" to Icons.Filled.Engineering,
        "users-round" to Icons.Filled.Groups,
        "help-circle" to Icons.Filled.Help,
        "shield-half" to Icons.Filled.Shield,
        "sliders-horizontal" to Icons.Filled.Tune,
        "heart-handshake" to Icons.Filled.Handshake,
    )

    val repli: ImageVector = Icons.Filled.Circle

    fun icone(lucide: String): ImageVector = parNom[lucide] ?: repli
}
