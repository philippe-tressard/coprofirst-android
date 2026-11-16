// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.apercu

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import fr.coprofirst.app.charte.Mouvement
import fr.coprofirst.app.commun.LocalHorsLigne
import fr.coprofirst.app.commun.animationsCoupees

/**
 * L'aperçu servi d'une saisie, à l'écran, comme `ApercuServi` de l'iOS : `contenu` reçoit la
 * valeur que le serveur a rendue pour `entree` — ou la dernière, tant qu'il n'a pas répondu ;
 * `null` avant sa première réponse. Le mécanisme (délai, réponse périmée, pas de
 * redemande) est `EtatApercu` ; cette vue n'y ajoute que le fondu : court, en sortie douce,
 * aucun sous « Supprimer les animations ».
 *
 * Le contenu RÉSERVE sa place (la ligne est là avant la première réponse) : rien ne bouge
 * sous le pouce quand la valeur arrive. `connue` : la valeur servie avec l'objet, pour sa
 * saisie d'ouverture — montrée d'emblée, jamais redemandée.
 */
@Composable
fun <E : Any, S : Any> ApercuServi(
    entree: E,
    demander: suspend (E) -> S,
    modifier: Modifier = Modifier,
    connue: Pair<E, S>? = null,
    contenu: @Composable (S?) -> Unit,
) {
    val etat = remember { EtatApercu(connue) }
    val portee = rememberCoroutineScope()
    val demande by rememberUpdatedState(demander)
    // Un appel ne part ni en consultation hors ligne, ni sans réseau ; il part au retour du réseau.
    val horsLigne = LocalHorsLigne.current
    val enLigne = horsLigne.consultation == null && horsLigne.enLigne
    LaunchedEffect(entree, enLigne) { etat.saisie(entree, portee, enLigne) { demande(it) } }
    val reduit = animationsCoupees()
    Crossfade(
        targetState = etat.valeur,
        modifier = modifier,
        animationSpec = if (reduit) snap() else tween(DUREE_FONDU_MS, easing = Mouvement.sortie),
        label = "apercu",
    ) { valeur -> contenu(valeur) }
}

/** `.easeOut(duration: 0.15)` de l'iOS. */
private const val DUREE_FONDU_MS = 150
