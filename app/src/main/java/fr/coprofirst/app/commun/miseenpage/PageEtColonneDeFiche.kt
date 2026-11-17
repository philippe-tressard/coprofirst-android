// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.miseenpage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R

/**
 * La page et la colonne de sa fiche, en fenêtre large (#69), comme `PageEtColonneDeFiche` de
 * l'iOS (coprofirst-ios#261) : `enColonne` — une liste occupe la colonne —, la page à gauche à
 * la largeur d'une liste (`ColonneDeFiche.largeurDeListe`), la fiche à droite, « Aucune
 * sélection » tant que rien n'est choisi ; sinon la page prend toute la largeur. La page garde
 * toujours la même place dans l'arbre : passer de l'un à l'autre ne la recrée pas (sa liste,
 * son onglet, sa recherche restent).
 *
 * `coupe` : un pliable déplié en livre (#242) — la page de gauche s'arrête au PLI, la fiche
 * commence après lui ; le pli lui-même sépare les deux pages, sans filet.
 */
@Composable
fun PageEtColonneDeFiche(enColonne: Boolean, page: @Composable () -> Unit, fiche: (@Composable () -> Unit)?, coupe: CoupeDeFenetre? = null) {
    var origine by remember { mutableFloatStateOf(0f) }
    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { origine = it.positionInWindow().x }) {
        val densite = LocalDensity.current
        val largeurDeListe = coupe?.let { with(densite) { (it.gauche - origine).toDp() }.coerceIn(0.dp, maxWidth) }
            ?: ColonneDeFiche.largeurDeListe(maxWidth)
        val pli = coupe?.let { with(densite) { (it.droite - it.gauche).toDp() } } ?: 0.dp
        Row(Modifier.fillMaxSize()) {
            Box(if (enColonne) Modifier.width(largeurDeListe).fillMaxHeight() else Modifier.weight(1f).fillMaxHeight()) { page() }
            if (enColonne) {
                if (coupe != null) Spacer(Modifier.width(pli)) else VerticalDivider(color = LocalCouleurs.current.border)
                Box(Modifier.weight(1f).fillMaxHeight().background(LocalCouleurs.current.bg).testTag(REPERE_COLONNE)) {
                    fiche?.invoke() ?: AucuneSelection()
                }
            }
        }
    }
}

/** La colonne vide : rien de choisi dans la liste. */
@Composable
private fun AucuneSelection() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            stringResource(R.string.colonne_aucune_selection),
            style = MaterialTheme.typography.bodyLarge,
            color = LocalCouleurs.current.textMuted,
        )
    }
}

/** Le repère de la colonne de fiche (tests d'interface). */
const val REPERE_COLONNE = "colonne.fiche"

/** Où la fenêtre se coupe en deux pages : de `gauche` à `droite`, en pixels de la fenêtre (le pli). */
data class CoupeDeFenetre(val gauche: Int, val droite: Int)
