// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.miseenpage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R

/**
 * La page et la colonne de sa fiche, en fenêtre large (#69), comme `PageEtColonneDeFiche` de
 * l'iOS (coprofirst-ios#261) : `enColonne` — une liste occupe la colonne —, la page à gauche à
 * la largeur d'une liste (`ColonneDeFiche.largeurDeListe`), la fiche à droite, « Aucune
 * sélection » tant que rien n'est choisi ; sinon la page prend toute la largeur. La page garde
 * toujours la même place dans l'arbre : passer de l'un à l'autre ne la recrée pas (sa liste,
 * son onglet, sa recherche restent).
 */
@Composable
fun PageEtColonneDeFiche(enColonne: Boolean, page: @Composable () -> Unit, fiche: (@Composable () -> Unit)?) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val largeurDeListe = ColonneDeFiche.largeurDeListe(maxWidth)
        Row(Modifier.fillMaxSize()) {
            Box(if (enColonne) Modifier.width(largeurDeListe).fillMaxHeight() else Modifier.weight(1f).fillMaxHeight()) { page() }
            if (enColonne) {
                VerticalDivider(color = LocalCouleurs.current.border)
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
