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
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry

/**
 * Un écran de menu dans une FENÊTRE LARGE, pour ses tests d'interface (#69) : la colonne de fiche
 * fournie, la page et sa fiche côte à côte comme la résidence les pose (`PageEtColonneDeFiche`,
 * la fiche que la liste tient, `ficheLocale`). Calé en haut à gauche, plus large que l'écran du
 * téléphone : il ne déborde qu'à droite. `taille` : celle du cadre — `TailleDeLEcran.ecran()`
 * pour qu'une colonne de droite qu'on TOUCHE reste à l'écran (320 dp en CI, #304).
 */
@Composable
fun EnFenetreLarge(taille: DpSize = DpSize(720.dp, 700.dp), page: @Composable () -> Unit) {
    val colonne = remember { ColonneDeFiche() }
    Box(Modifier.wrapContentSize(Alignment.TopStart, unbounded = true).requiredSize(taille)) {
        CompositionLocalProvider(LocalColonneDeFiche provides colonne) {
            PageEtColonneDeFiche(colonne.occupee, page = page, fiche = colonne.ficheLocale())
        }
    }
}

/** La taille de l'écran de l'émulateur, en dp : un cadre qui y tient tout entier. */
object TailleDeLEcran {
    fun ecran(): DpSize {
        val mesures = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics
        return DpSize((mesures.widthPixels / mesures.density).dp, (mesures.heightPixels / mesures.density).dp)
    }
}
