// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun

import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.charte.Mise
import kotlinx.coroutines.delay

/** Le repère de la bulle, pour les tests d'interface. */
const val REPERE_AVIS = "avis"

/**
 * Le contenu, et par-dessus, EN BAS, l'avis qui suit un geste — comme `.avisEphemere` de
 * l'iOS : il monte du bas, reste le temps du site (`AvisDuSite.DUREE_MS`, généré), puis se
 * retire ; un fondu seul quand les animations de l'appareil sont coupées. Il est annoncé au
 * lecteur d'écran (région vivante) et suivi d'un retour haptique selon son ton.
 * ```
 * val avis = remember { AvisDuGeste() }
 * AvisEphemere(avis) { … avis.dire(ressources.getString(R.string.acces_enregistre)) … }
 * ```
 */
@Composable
fun AvisEphemere(avis: AvisDuGeste, modifier: Modifier = Modifier, contenu: @Composable BoxScope.() -> Unit) {
    Box(modifier) {
        contenu()
        BulleAvis(avis, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun BulleAvis(avis: AvisDuGeste, modifier: Modifier) {
    val phrase = avis.phrase
    val haptique = LocalHapticFeedback.current
    val reduit = animationsCoupees()
    LaunchedEffect(phrase) {
        if (phrase == null) return@LaunchedEffect
        haptique.performHapticFeedback(retour(phrase.ton))
        // Remplacée avant la fin : l'effet est annulé, la phrase suivante reste.
        delay(AvisDuSite.DUREE_MS)
        avis.retirer(phrase)
    }
    AnimatedContent(
        targetState = phrase,
        modifier = modifier,
        transitionSpec = {
            val duree = tween<Float>(DUREE_ANIMATION_MS)
            if (reduit) {
                fadeIn(duree) togetherWith fadeOut(duree)
            } else {
                (fadeIn(duree) + slideInVertically(tween(DUREE_ANIMATION_MS)) { it }) togetherWith
                    (fadeOut(duree) + slideOutVertically(tween(DUREE_ANIMATION_MS)) { it })
            }
        },
        contentKey = { it?.numero },
        label = "avis",
    ) { montree ->
        if (montree != null) Bulle(montree)
    }
}

@Composable
private fun Bulle(phrase: PhraseAffichee) {
    val couleurs = LocalCouleurs.current
    Row(
        Modifier
            .padding(horizontal = Mise.marge)
            .padding(bottom = 12.dp)
            .background(couleurs.text.copy(alpha = OPACITE_BULLE), CircleShape)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
            .testTag(REPERE_AVIS),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(pictogramme(phrase.ton), contentDescription = null, tint = couleurs.textInverse, modifier = Modifier.size(20.dp))
        Text(
            phrase.texte,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = couleurs.textInverse,
        )
    }
}

private fun pictogramme(ton: TonAvis): ImageVector = when (ton) {
    TonAvis.Succes -> Icons.Filled.CheckCircle
    TonAvis.Avertissement -> Icons.Filled.Warning
    TonAvis.Erreur -> Icons.Filled.Cancel
    TonAvis.Info -> Icons.Filled.Info
}

private fun retour(ton: TonAvis): HapticFeedbackType = when (ton) {
    TonAvis.Succes -> HapticFeedbackType.Confirm
    TonAvis.Avertissement, TonAvis.Erreur -> HapticFeedbackType.Reject
    TonAvis.Info -> HapticFeedbackType.ContextClick
}

/**
 * Les animations coupées par l'appareil (« Supprimer les animations ») : le mouvement réduit
 * d'Android — écrit ici une fois, lu aussi par `ChoixPerimetres` et `ApercuServi`.
 */
@Composable
internal fun animationsCoupees(): Boolean {
    val contexte = LocalContext.current
    return remember(contexte) {
        Settings.Global.getFloat(contexte.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/** La bulle de l'iOS : le texte de la charte à 92 %, sous le texte inverse. */
private const val OPACITE_BULLE = 0.92f
private const val DUREE_ANIMATION_MS = 250
