// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.miseenpage

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Un écran de menu dans une FENÊTRE LARGE, pour ses tests d'interface (#69) : la colonne de fiche
 * fournie, la page et sa fiche côte à côte comme la résidence les pose (`PageEtColonneDeFiche`,
 * la fiche que la liste tient, `ficheLocale`). Calé en haut à gauche, plus large que l'écran du
 * téléphone : il ne déborde qu'à droite.
 */
@Composable
fun EnFenetreLarge(page: @Composable () -> Unit) {
    val colonne = remember { ColonneDeFiche() }
    Box(Modifier.wrapContentSize(Alignment.TopStart, unbounded = true).requiredSize(720.dp, 700.dp)) {
        CompositionLocalProvider(LocalColonneDeFiche provides colonne) {
            PageEtColonneDeFiche(colonne.occupee, page = page, fiche = colonne.ficheLocale())
        }
    }
}
