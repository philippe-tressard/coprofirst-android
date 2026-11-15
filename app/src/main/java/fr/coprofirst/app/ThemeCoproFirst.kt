// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Thème de l'app. Aucune couleur écrite à la main : la charte de CoproFirst se
 * GÉNÉRERA depuis ses sources (palettes de la release). D'ici là, les couleurs du
 * système (Material You dès Android 12) ou celles de Material par défaut.
 */
@Composable
fun ThemeCoproFirst(contenu: @Composable () -> Unit) {
    val sombre = isSystemInDarkTheme()
    val couleurs = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val contexte = LocalContext.current
            if (sombre) dynamicDarkColorScheme(contexte) else dynamicLightColorScheme(contexte)
        }
        sombre -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = couleurs, content = contenu)
}
