// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.charte

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Les icônes du site (noms Lucide) en icônes de Material, comme l'iOS les traduit en SF
 * Symbols (`Pictogrammes`). Un nom absent prend le repli — et des tests refusent qu'une
 * page de navigation, une rubrique du fil ou une nature d'affaire générées en manquent.
 *
 * ⚠️ Divergence déclarée : la gouttière du fil de l'iOS dessine les TRACÉS Lucide du site
 * (`icones-svg.json` de coprofirst, #85) ; ce catalogue n'est pas publié avec la release
 * (`tables-clients.json`), l'app Android en reste à leur équivalent de Material.
 */
object Pictogrammes {
    val parNom: Map<String, ImageVector> = mapOf(
        // Les pages de navigation.
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
        // Les rubriques du fil (`naturesDuFil`) et les natures d'une affaire (`naturesAffaire`).
        "circle" to Icons.Filled.Circle,
        "circle-check" to Icons.Filled.CheckCircle,
        "wrench" to Icons.Filled.Build,
        "refresh-cw" to Icons.Filled.Refresh,
        "activity" to Icons.Filled.MonitorHeart,
        "lightbulb" to Icons.Filled.Lightbulb,
        "newspaper" to Icons.Filled.Newspaper,
        "file-text" to Icons.Filled.Description,
        "bar-chart-3" to Icons.Filled.BarChart,
        "book-open" to Icons.AutoMirrored.Filled.MenuBook,
        "calendar-days" to Icons.Filled.CalendarMonth,
        // Les lecteurs d'un contenu (`lecture.icones`, « Qui la lit ») et son cadenas.
        "home" to Icons.Filled.Home,
        "user" to Icons.Filled.Person,
        "shield-check" to Icons.Filled.VerifiedUser,
        "lock" to Icons.Filled.Lock,
    )

    val repli: ImageVector = Icons.Filled.Circle

    fun icone(lucide: String): ImageVector = parNom[lucide] ?: repli
}
