// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.espacecs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.commun.Carte

/**
 * La carte d'une ligne d'une file de travail (comptes, commandes, demandes de profil du
 * Paramétrage), comme `CarteDeFile` de l'iOS : son contenu, puis « Approuver » et
 * « Rejeter » — côte à côte, l'un sous l'autre aux grandes tailles de texte. Pendant
 * `enCours`, les deux attendent. Repères : `<repere>`, `<repere>.approuver`, `<repere>.rejeter`.
 *
 * ⚠️ Divergence déclarée : l'iOS teinte « Approuver » d'un aplat vert (`successPlein`) ;
 * l'aplat s'écrit une fois, `BoutonPlein`, aux couleurs du primaire. Ici, les deux gestes
 * sont des contours teintés (vert, rouge) — même sens, même place.
 */
@Composable
fun CarteDeFile(
    repere: String,
    enCours: Boolean,
    approuver: () -> Unit,
    rejeter: () -> Unit,
    contenu: @Composable ColumnScope.() -> Unit,
) {
    val couleurs = LocalCouleurs.current
    val grand = LocalDensity.current.fontScale >= GRANDE_ECHELLE
    Carte(Modifier.testTag(repere)) {
        Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(4.dp)) { contenu() }
        Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
            val boutons: @Composable (Modifier) -> Unit = { m ->
                Geste(R.string.espacecs_approuver, Icons.Filled.Check, Teinte(couleurs.successTexte, couleurs.successFond, couleurs.successBordure), enCours, "$repere.approuver", m, approuver)
                Geste(R.string.espacecs_rejeter, Icons.Filled.Close, Teinte(couleurs.danger, couleurs.dangerFond, couleurs.dangerBordure), enCours, "$repere.rejeter", m, rejeter)
            }
            if (grand) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { boutons(Modifier.fillMaxWidth()) }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { boutons(Modifier.weight(1f)) }
            }
            if (enCours) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = couleurs.primary)
        }
    }
}

/** Le texte, le fond et le filet d'un geste. */
private data class Teinte(val texte: Color, val fond: Color, val bordure: Color)

@Composable
private fun Geste(
    libelle: Int,
    icone: ImageVector,
    teinte: Teinte,
    enCours: Boolean,
    repere: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = !enCours,
        border = BorderStroke(1.dp, teinte.bordure),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = teinte.fond, contentColor = teinte.texte),
        modifier = modifier.heightIn(min = Mise.cible).testTag(repere),
    ) {
        Icon(icone, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(stringResource(libelle), Modifier.padding(start = 6.dp))
    }
}

/** L'échelle du texte au-delà de laquelle les deux gestes s'empilent (les tailles d'accessibilité de l'iOS). */
private const val GRANDE_ECHELLE = 1.5f
