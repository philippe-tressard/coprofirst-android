// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.texte

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.coprofirst.app.LocalCouleurs

/** La colonne du marqueur (« • », « 12. ») et le retrait d'un niveau : en `sp`, elle suit la taille du texte. */
private val COLONNE = 26.sp

/**
 * Le texte riche du site dans un détail, comme l'iOS (`TexteRiche`) : les blocs de
 * `TexteDuSite`, mis en page.
 *
 * Un élément de liste garde son marqueur et son texte sur la même ligne, et les lignes
 * suivantes s'alignent sur le texte, pas sous le marqueur — le retrait suspendu qu'un seul
 * `Text` ne sait pas faire. Police (`style`) et couleur (`couleur`) viennent de l'appelant ;
 * les liens prennent `primary` de la charte et s'ouvrent par le gestionnaire d'adresses du
 * système (`LinkAnnotation.Url`). Un texte vide ne montre rien : `TexteDuSite.blocs(html)`
 * dit s'il l'est.
 */
@Composable
fun TexteRiche(
    html: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    couleur: Color = LocalCouleurs.current.text,
) {
    val lien = LocalCouleurs.current.primary
    val blocs = remember(html, lien) { TexteDuSite.blocs(html, TextLinkStyles(SpanStyle(color = lien))) }
    val colonne = with(LocalDensity.current) { COLONNE.toDp() }
    Column(modifier) {
        blocs.forEachIndexed { rang, bloc ->
            val haut = when {
                rang == 0 -> 0.dp
                bloc.ecart -> 12.dp
                else -> 4.dp
            }
            Ligne(bloc, style, couleur, colonne, Modifier.padding(top = haut))
        }
    }
}

@Composable
private fun Ligne(bloc: BlocDuSite, style: TextStyle, couleur: Color, colonne: Dp, modifier: Modifier) {
    if (bloc.niveau == 0) {
        Text(bloc.texte, modifier, color = couleur, style = style)
        return
    }
    val marqueur = bloc.marqueur
    Row(modifier.padding(start = colonne * (bloc.niveau - 1))) {
        Text(
            marqueur.orEmpty(),
            Modifier
                .widthIn(min = colonne)
                .alignByBaseline()
                .then(if (marqueur == null) Modifier.clearAndSetSemantics {} else Modifier),
            color = couleur,
            style = style.copy(fontFeatureSettings = "tnum"), // chiffres de même chasse
        )
        Text(bloc.texte, Modifier.weight(1f).alignByBaseline(), color = couleur, style = style)
    }
}
