// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.charte

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * Les mesures communes des écrans, comme `Mise` de l'iOS. Chacune est PROPRE À L'APP,
 * déclarée avec sa raison et sa date dans `MesuresTest` ; une mesure dont le site a le jeton
 * se lit dans `MesuresDuSite` (généré, #2). ⚠️ Divergence déclarée : la cible tactile est de
 * 48 dp, la règle d'Android (44 pt sur iOS).
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

    /**
     * La largeur de lecture d'un texte, d'une fiche, d'une page de réglages (`Mise.lecture` de
     * l'iOS, #69, coprofirst-ios#223) : sur une tablette en paysage, une ligne ne s'étire pas
     * sur toute la fenêtre. Se pose par `largeurDeLecture()`, jamais par un `widthIn` à la main.
     */
    val lecture = 720.dp

    /** Celle, plus étroite, d'un formulaire de l'entrée : l'adresse, la connexion, le retour. */
    val formulaire = 480.dp
}

/**
 * Borne la largeur à `base` (`Mise.lecture` par défaut), agrandie avec le texte (la taille de
 * police de l'appareil, comme Dynamic Type à l'iOS) : une ligne garde la même longueur en
 * caractères. Sans effet sur un téléphone, plus étroit que la borne. Suivie de `fillMaxWidth()`,
 * centrée par son parent.
 */
fun Modifier.largeurDeLecture(base: Dp = Mise.lecture): Modifier = layout { mesurable, contraintes ->
    val borne = (base.toPx() * fontScale.coerceAtLeast(1f)).roundToInt()
    val max = minOf(contraintes.maxWidth, borne)
    val place = mesurable.measure(contraintes.copy(minWidth = minOf(contraintes.minWidth, max), maxWidth = max))
    layout(place.width, place.height) { place.place(0, 0) }
}

/** Le mouvement commun des écrans. */
object Mouvement {
    /**
     * La courbe d'entrée : ease-out marquée (`cubic-bezier(0.23, 1, 0.32, 1)`) — ce qui
     * paraît part vite et se pose doucement. Le passage d'un écran, l'apparition d'une
     * rangée, le fondu d'un aperçu servi.
     */
    val sortie: Easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
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
