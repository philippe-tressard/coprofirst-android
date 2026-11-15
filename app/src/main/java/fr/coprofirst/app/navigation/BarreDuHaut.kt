// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.navigation

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue

/**
 * Les actions de la barre du haut, que l'écran ouvert déclare — comme le `toolbar` de
 * l'iOS (le « + » de Mes lots & accès). Une seule déclaration à la fois : celle de
 * l'écran affiché ; elle se retire quand il quitte l'écran.
 */
class BarreDuHaut {
    internal var actions by mutableStateOf<(@Composable RowScope.() -> Unit)?>(null)
}

/**
 * Pose `actions` dans la barre du haut tant que l'écran qui l'appelle est affiché :
 * ```
 * ActionsDeBarre(contexte) {
 *     IconButton(onClick = { … }) { Icon(Icons.Filled.Add, contentDescription = "Nouvel accès") }
 * }
 * ```
 * Une icône seule porte toujours sa `contentDescription` (le lecteur d'écran la dit).
 */
@Composable
fun ActionsDeBarre(contexte: ContexteDeMenu, actions: @Composable RowScope.() -> Unit) =
    ActionsDeBarre(contexte.barre, actions)

@Composable
fun ActionsDeBarre(barre: BarreDuHaut, actions: @Composable RowScope.() -> Unit) {
    val courantes by rememberUpdatedState(actions)
    val stable: @Composable RowScope.() -> Unit = { courantes() }
    DisposableEffect(barre) {
        barre.actions = stable
        onDispose { if (barre.actions === stable) barre.actions = null }
    }
}

/** Les actions déclarées, à poser dans `TopAppBar(actions = …)`. */
@Composable
fun RowScope.ActionsDeclarees(barre: BarreDuHaut) {
    barre.actions?.invoke(this)
}
