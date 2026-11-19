// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.fil

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/**
 * Le zoom du carnet (#286), comme `ZoomDuCarnet` de l'iOS (coprofirst-ios#277) : PINCER la page
 * du jour ouvre sa semaine, ÉCARTER les doigts sur un jour de la semaine le rouvre. La page suit
 * les doigts (1:1 jusqu'au seuil, puis élastique `x·c/(c+x)`) ; sous le seuil, elle revient d'un
 * ressort amorti, interruptible. Le passage se décide sur l'écart (0,15) ou sur un geste vif dans
 * le bon sens. Un seul doigt n'est pas le sien : la page défile et tourne comme avant.
 */
object ZoomDuCarnet {
    enum class Sens { Pincer, Ecarter }

    /** L'écart d'échelle qui fait passer : 15 %. */
    const val SEUIL = 0.15f

    /** La vitesse d'échelle (par seconde) d'un geste vif, qui passe même sous le seuil. */
    const val VIF = 1.2f

    /** La constante de l'élastique, au-delà du seuil. */
    private const val ELASTIQUE = 0.25f

    /** L'échelle montrée pour l'échelle des doigts : 1:1, puis élastique au-delà du seuil. */
    fun montree(echelle: Float): Float {
        val ecart = echelle - 1f
        val absolu = abs(ecart)
        if (absolu <= SEUIL) return echelle
        val dela = absolu - SEUIL
        return 1f + sign(ecart) * (SEUIL + dela * ELASTIQUE / (ELASTIQUE + dela))
    }

    /** Le geste lâché à `echelle`, à `vitesse` (d'échelle, par seconde) : passe-t-il ? */
    fun passe(sens: Sens, echelle: Float, vitesse: Float): Boolean = when (sens) {
        Sens.Pincer -> echelle <= 1f - SEUIL || (vitesse <= -VIF && echelle < 1f)
        Sens.Ecarter -> echelle >= 1f + SEUIL || (vitesse >= VIF && echelle > 1f)
    }
}

/**
 * Le geste à deux doigts, dans le `sens` voulu : la page suit (`graphicsLayer`), puis `passer`
 * si le geste passe, ou le ressort la ramène. `fige` (le mouvement réduit) : la page ne suit pas,
 * le passage se fait d'un fondu, ailleurs. Lu AVANT le défilement (`Initial`) : deux doigts ne
 * font pas défiler la liste ; un doigt n'est pas consommé.
 */
@Composable
internal fun Modifier.zoomDuCarnet(sens: ZoomDuCarnet.Sens, fige: Boolean, passer: () -> Unit): Modifier {
    val echelle = remember { Animatable(1f) }
    val portee = rememberCoroutineScope()
    val action = rememberUpdatedState(passer)
    return this
        .graphicsLayer {
            val e = echelle.value
            scaleX = e
            scaleY = e
        }
        .pointerInput(sens, fige) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                var cumul = 1f
                var vitesse = 0f
                var avant = 0L
                var deux = false
                while (true) {
                    val evenement = awaitPointerEvent(PointerEventPass.Initial)
                    val appuyes = evenement.changes.filter { it.pressed }
                    if (appuyes.isEmpty()) break
                    if (appuyes.size < 2) continue
                    if (!deux) portee.launch { echelle.stop() }
                    deux = true
                    val zoom = evenement.calculateZoom()
                    val instant = evenement.changes.first().uptimeMillis
                    if (avant > 0L && instant > avant) vitesse = (zoom - 1f) * cumul * 1000f / (instant - avant)
                    avant = instant
                    cumul *= zoom
                    evenement.changes.forEach { it.consume() }
                    if (!fige) portee.launch { echelle.snapTo(ZoomDuCarnet.montree(cumul)) }
                }
                if (!deux) return@awaitEachGesture
                if (ZoomDuCarnet.passe(sens, cumul, vitesse)) {
                    portee.launch { echelle.snapTo(1f) }
                    action.value()
                } else if (!fige) {
                    portee.launch { echelle.animateTo(1f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)) }
                }
            }
        }
}
