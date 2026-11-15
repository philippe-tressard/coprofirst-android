// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.charte

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Les mesures communes des écrans, comme `Mise` de l'iOS. ⚠️ Divergence déclarée : la
 * cible tactile est de 48 dp, la règle d'Android (44 pt sur iOS).
 */
object Mise {
    /** Le rayon d'une carte, d'un champ, d'un message. */
    val rayon = 14.dp

    /** La marge d'un écran. */
    val marge = 20.dp

    /** La plus petite cible tactile. */
    val cible = 48.dp

    /** L'espace entre deux cartes d'une liste. */
    val ecart = 12.dp

    /** La hauteur de la mosaïque des photos d'un élément ouvert (`MosaiquePhotos`). */
    val mosaique = 220.dp

    /** La même, sous une suite de l'Historique ou dans un message. */
    val mosaiqueCompacte = 140.dp

    /** Le côté de la vignette d'une carte du fil (`VignettePieces`). */
    val vignette = 56.dp
}

/** Les styles de texte de la charte, au-delà de ceux de Material. */
object Typo {
    /** Le titre d'un écran ou d'une section, en serif comme l'iOS (`Font.titre`). */
    @Composable
    fun titre(base: TextStyle = MaterialTheme.typography.headlineSmall): TextStyle =
        base.copy(fontFamily = FontFamily.Serif)

    /**
     * Le libellé d'une section ou d'un champ (`.styleLibelle()` de l'iOS) : petites
     * capitales espacées, en `textMuted` (la couleur se pose à l'appel).
     */
    @Composable
    fun libelle(): TextStyle =
        MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp)
}
