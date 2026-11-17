// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.montre

import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import fr.coprofirst.app.charte.Palettes

/**
 * Le thème de la montre : la charte de CoproFirst (`seine`) en SOMBRE, générée depuis
 * coprofirst (`Palettes`, :partage-android, #206), jamais recopiée. Le fond reste le noir de
 * Wear OS. `error` porte la marque de danger de la charte : celle des alertes urgentes.
 */
@Composable
fun ThemeMontre(contenu: @Composable () -> Unit) {
    val charte = Palettes.pour(null).sombre
    MaterialTheme(
        colorScheme = ColorScheme(
            primary = charte.primary,
            onPrimary = charte.surPlein,
            error = charte.danger,
            onSurface = charte.text,
            onSurfaceVariant = charte.textMuted,
        ),
        content = contenu,
    )
}
