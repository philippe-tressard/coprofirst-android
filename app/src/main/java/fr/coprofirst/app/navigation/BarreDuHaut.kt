// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.charte.Typo
import kotlin.reflect.KMutableProperty1

/**
 * Ce que l'écran ouvert déclare dans la barre du haut — comme le `toolbar`, le
 * `.navigationTitle` et le `.navigationSubtitle` de l'iOS : ses actions (le « + » de Mes lots
 * & accès), son titre, son sous-titre. Une seule déclaration de chaque à la fois : celle de
 * l'écran affiché ; elle se retire quand il quitte l'écran.
 */
class BarreDuHaut {
    internal var actions by mutableStateOf<(@Composable RowScope.() -> Unit)?>(null)
    var titre by mutableStateOf<String?>(null)
        internal set
    var sousTitre by mutableStateOf<String?>(null)
        internal set

    companion object {
        /** Le repère du sous-titre affiché (`EnTeteDeBarre`). */
        const val REPERE_SOUS_TITRE = "barre.sousTitre"
    }
}

/**
 * Pose `valeur` à sa `place` de la barre tant que l'écran qui l'appelle est affiché, et l'en
 * retire quand il part — sauf si un autre écran l'a remplacée entre-temps.
 */
@Composable
private fun <T : Any> Declarer(barre: BarreDuHaut, place: KMutableProperty1<BarreDuHaut, T?>, valeur: T?) {
    DisposableEffect(barre, valeur) {
        place.set(barre, valeur)
        onDispose { if (place.get(barre) === valeur) place.set(barre, null) }
    }
}

/**
 * Le titre de la barre du haut, posé par l'écran affiché tant qu'il l'est — le
 * `.navigationTitle` de l'iOS : le numéro d'une affaire poussée par la porte, plutôt que
 * « Affaire ». Sans lui, la barre porte le titre de la page ou de la fiche.
 */
@Composable
fun TitreDeBarre(barre: BarreDuHaut, titre: String?) = Declarer(barre, BarreDuHaut::titre, titre)

/**
 * Le sous-titre de la barre du haut, sous le titre, posé par l'écran affiché tant qu'il l'est
 * — le `.navigationSubtitle` de l'iOS : « 12 non lues » sous « Notifications ». `null` : pas
 * de sous-titre. Dans la barre de la résidence comme dans celle d'une feuille pleine.
 */
@Composable
fun SousTitreDeBarre(barre: BarreDuHaut, texte: String?) = Declarer(barre, BarreDuHaut::sousTitre, texte)

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
    // UNE déclaration pour la vie de l'écran : elle appelle toujours les actions courantes.
    val stable = remember<@Composable RowScope.() -> Unit> { { courantes() } }
    Declarer(barre, BarreDuHaut::actions, stable)
}

/** Les actions déclarées, à poser dans `TopAppBar(actions = …)`. */
@Composable
fun RowScope.ActionsDeclarees(barre: BarreDuHaut) {
    barre.actions?.invoke(this)
}

/**
 * Le titre d'une barre (en serif, annoncé comme titre) et, dessous, le sous-titre déclaré
 * (`SousTitreDeBarre`), plus petit et sourd — la barre de la résidence et celle d'une
 * feuille pleine.
 */
@Composable
fun EnTeteDeBarre(titre: String, sousTitre: String?, modifier: Modifier = Modifier) {
    val couleurs = LocalCouleurs.current
    Column(modifier) {
        Text(
            titre,
            style = Typo.titre(),
            color = couleurs.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() },
        )
        if (sousTitre != null) {
            Text(
                sousTitre,
                style = MaterialTheme.typography.labelMedium,
                color = couleurs.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag(BarreDuHaut.REPERE_SOUS_TITRE),
            )
        }
    }
}
