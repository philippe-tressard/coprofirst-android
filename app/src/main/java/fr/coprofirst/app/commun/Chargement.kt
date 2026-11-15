// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.session.ErreurService

/** Où en est un chargement. */
sealed interface EtatChargement<out T> {
    data object EnCours : EtatChargement<Nothing>
    data class Pret<T>(val valeur: T) : EtatChargement<T>
    data class Echec(val erreur: ErreurService) : EtatChargement<Nothing>
}

/**
 * Charger, puis montrer — jamais un écran blanc, comme l'iOS (`Chargement`) : le
 * squelette pendant le chargement, une phrase et « Réessayer » sur une erreur, le contenu
 * ensuite. `recharger` (donné au contenu) relance ; `cle` relance quand elle change.
 *
 * Le squelette est un rendu factice du contenu, estompé : il réserve la place, ne
 * répond pas au toucher, et le lecteur d'écran l'annonce « Chargement ».
 */
@Composable
fun <T> Chargement(
    charger: suspend () -> T,
    modifier: Modifier = Modifier,
    cle: Any? = Unit,
    squelette: @Composable () -> Unit,
    contenu: @Composable (valeur: T, recharger: () -> Unit) -> Unit,
) {
    var essai by remember { mutableIntStateOf(0) }
    var etat by remember(cle) { mutableStateOf<EtatChargement<T>>(EtatChargement.EnCours) }
    LaunchedEffect(cle, essai) {
        etat = try {
            EtatChargement.Pret(charger())
        } catch (erreur: ErreurService) {
            EtatChargement.Echec(erreur)
        }
    }
    val annonce = stringResource(R.string.chargement)
    AnimatedContent(
        targetState = etat,
        transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) },
        contentKey = { it::class },
        modifier = modifier,
        label = "chargement",
    ) { courant ->
        when (courant) {
            EtatChargement.EnCours -> Box(Modifier.alpha(0.35f).semantics(mergeDescendants = true) { contentDescription = annonce }) {
                squelette()
            }
            is EtatChargement.Pret -> contenu(courant.valeur) { essai++ }
            is EtatChargement.Echec -> MessageErreur(courant.erreur, Modifier.padding(Mise.marge)) {
                etat = EtatChargement.EnCours
                essai++
            }
        }
    }
}
