// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Des pastilles côte à côte, qui passent à la ligne au bord (grand texte, libellés longs),
 * comme `FlowCapsules` de l'iOS : les `PastilleTeinte(…, compacte = true)` d'une carte
 * (type, état, rôles d'un compte ; type, spécialité, contrats d'un prestataire).
 */
@Composable
fun FlowCapsules(modifier: Modifier = Modifier, contenu: @Composable FlowRowScope.() -> Unit) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(ESPACE),
        verticalArrangement = Arrangement.spacedBy(ESPACE),
        content = contenu,
    )
}

/** L'espace de l'iOS (`RangeesQuiPassent(espacement: 6)`). */
private val ESPACE = 6.dp
