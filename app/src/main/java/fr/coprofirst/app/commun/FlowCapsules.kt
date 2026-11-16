// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Des pastilles côte à côte, qui passent à la ligne au bord (grand texte, libellés longs),
 * comme `FlowCapsules` de l'iOS : les `PastilleTeinte(…, compacte = true)` d'une carte
 * (type, état, rôles d'un compte ; type, spécialité, contrats d'un prestataire). `espace` :
 * 8 dp pour des `Pastille` touchables (`FlowEtiquettes` de l'iOS, `ChoixPerimetres`).
 *
 * Une pastille plus large que la rangée y prend une ligne à elle et passe à la ligne dans sa
 * largeur, au plus grand texte compris : `FlowRow` borne chaque enfant à la largeur de la
 * rangée (le défaut corrigé par coprofirst-ios#202 n'existe pas ici) — `FlowCapsulesInterfaceTest`
 * le mesure (#61).
 */
@Composable
fun FlowCapsules(modifier: Modifier = Modifier, espace: Dp = ESPACE, contenu: @Composable FlowRowScope.() -> Unit) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(espace),
        verticalArrangement = Arrangement.spacedBy(espace),
        content = contenu,
    )
}

/** L'espace de l'iOS (`RangeesQuiPassent(espacement: 6)`). */
private val ESPACE = 6.dp
