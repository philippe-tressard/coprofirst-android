// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.charte.Couleurs

/** Un libellé et sa classe `badge-*`, tels qu'une table du site les écrit. */
data class LibelleTeinte(val libelle: String, val badge: String) {
    companion object {
        /** Une valeur inconnue de la table s'affiche brute, en gris : un libellé manquant doit se voir. */
        fun brut(valeur: String) = LibelleTeinte(valeur, "badge-gray")
    }
}

/**
 * Une pastille teintée, comme l'iOS (`PastilleTeinte`) : un libellé, et la classe
 * `badge-*` que le site lui donne (une couleur d'état de la charte). `compacte` pour
 * une ligne dense.
 */
@Composable
fun PastilleTeinte(libelle: String, badge: String, modifier: Modifier = Modifier, compacte: Boolean = false) {
    val (texte, fond) = teintes(badge, LocalCouleurs.current)
    Text(
        libelle,
        style = (if (compacte) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium)
            .copy(fontWeight = FontWeight.SemiBold),
        color = texte,
        modifier = modifier
            .background(fond, CircleShape)
            .padding(horizontal = if (compacte) 8.dp else 10.dp, vertical = if (compacte) 3.dp else 4.dp),
    )
}

@Composable
fun PastilleTeinte(teinte: LibelleTeinte, modifier: Modifier = Modifier, compacte: Boolean = false) =
    PastilleTeinte(teinte.libelle, teinte.badge, modifier, compacte)

/** Les couleurs d'état de la charte (texte, fond) pour chaque classe du site — celles de l'iOS. */
fun teintes(badge: String, c: Couleurs): Pair<Color, Color> = when (badge) {
    "badge-blue" -> c.info to c.infoFond
    "badge-purple" -> c.violet to c.violetFond
    "badge-orange" -> c.orangeTexte to c.orangeFond
    "badge-yellow" -> c.jauneTexte to c.jauneFond
    "badge-green" -> c.successTexte to c.successFond
    "badge-red" -> c.danger to c.dangerFond
    else -> c.textMuted to c.neutreFond
}
