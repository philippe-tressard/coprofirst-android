// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import fr.coprofirst.app.charte.Couleurs
import fr.coprofirst.app.charte.Palette
import fr.coprofirst.app.charte.Palettes

/** Les couleurs de la charte en cours (clair ou sombre), pour ce que Material ne nomme pas. */
val LocalCouleurs = staticCompositionLocalOf { Palettes.pour(null).clair }

/**
 * Le thème de l'app : la palette de CoproFirst, GÉNÉRÉE depuis la charte du site — la
 * charte (`seine`) par défaut, celle de la résidence dès qu'elle est connue, comme
 * l'iOS. Aucune couleur écrite à la main : Material lit les jetons de la palette.
 */
@Composable
fun ThemeCoproFirst(palette: Palette = Palettes.pour(null), contenu: @Composable () -> Unit) {
    val sombre = isSystemInDarkTheme()
    val couleurs = if (sombre) palette.sombre else palette.clair
    CompositionLocalProvider(LocalCouleurs provides couleurs) {
        MaterialTheme(colorScheme = schema(couleurs, sombre), content = contenu)
    }
}

/**
 * Les rôles de Material, tirés des jetons de la charte, comme l'iOS les emploie : les
 * liens, la coche, le focus prennent `primary` ; le fond d'un bouton plein,
 * `primaryPlein` — il passe par `BoutonPlein`, et lui seul.
 */
private fun schema(c: Couleurs, sombre: Boolean): ColorScheme {
    val base = if (sombre) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = c.primary,
        // Ce qui se pose sur `primary` (case cochée…) : le fond de page, lisible dans les deux modes.
        onPrimary = c.bg,
        primaryContainer = c.primaryLight,
        onPrimaryContainer = c.primary,
        secondary = c.secondary,
        onSecondary = c.surPlein,
        tertiary = c.accentPlein,
        onTertiary = c.surAccent,
        background = c.bg,
        onBackground = c.text,
        surface = c.bg,
        onSurface = c.text,
        surfaceVariant = c.neutreFond,
        onSurfaceVariant = c.textMuted,
        surfaceContainerLowest = c.surface,
        surfaceContainerLow = c.surface,
        surfaceContainer = c.surface,
        surfaceContainerHigh = c.surface,
        surfaceContainerHighest = c.neutreFond,
        outline = c.borderChamp,
        outlineVariant = c.border,
        error = c.danger,
        onError = c.surPlein,
        errorContainer = c.dangerFond,
        onErrorContainer = c.danger,
        scrim = c.voileModale,
    )
}
