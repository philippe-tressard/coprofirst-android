// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.charte.Typo
import fr.coprofirst.app.commun.formats.FormatsDuSite

/** Comment un chiffre alerte : en orange (une file à traiter), en rouge (un retard). */
enum class AlerteChiffre { Avertissement, Danger }

/**
 * Un chiffre (`kpi-card` du site), comme l'iOS (`CompteurFile`, `Chiffre`) : la valeur, et
 * ce qu'elle compte ; teinté quand il alerte. Un seul composant pour les compteurs de la
 * file de travail (`commun/validations`) et ceux du Reporting de l'Espace CS.
 */
@Composable
fun Chiffre(
    valeur: Int,
    libelle: String,
    modifier: Modifier = Modifier,
    alerte: AlerteChiffre? = null,
    style: TextStyle = MaterialTheme.typography.headlineMedium,
    repere: String? = null,
) {
    val c = LocalCouleurs.current
    val (fond, filet, texte) = when (alerte) {
        AlerteChiffre.Avertissement -> Triple(c.warningFond, c.warningBordure, c.warningTexte)
        AlerteChiffre.Danger -> Triple(c.dangerFond, c.dangerBordure, c.danger)
        null -> Triple(c.surface, c.border, c.text)
    }
    val forme = RoundedCornerShape(Mise.rayon)
    Column(
        modifier
            .background(fond, forme)
            .border(1.dp, filet, forme)
            .padding(12.dp)
            .semantics(mergeDescendants = true) {}
            .then(if (repere != null) Modifier.testTag(repere) else Modifier),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(FormatsDuSite.nombre(valeur), style = Typo.titre(style), color = texte)
        Text(libelle, style = MaterialTheme.typography.bodySmall, color = c.textMuted)
    }
}

/** Des chiffres côte à côte, de même largeur — l'un sous l'autre aux grandes tailles de texte. */
@Composable
fun RangeeDeChiffres(contenu: @Composable (Modifier) -> Unit) {
    if (LocalDensity.current.fontScale >= GRANDE_ECHELLE) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { contenu(Modifier.fillMaxWidth()) }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { contenu(Modifier.weight(1f)) }
    }
}

/** L'échelle du texte au-delà de laquelle ce qui va côte à côte s'empile (les tailles d'accessibilité de l'iOS). */
internal const val GRANDE_ECHELLE = 1.5f
