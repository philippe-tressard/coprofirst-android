// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.affaires

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.charte.Mouvement
import fr.coprofirst.app.commun.animationsCoupees

/**
 * La phrase discrète en tête du formulaire (`FormeDeSaisie.phrase` de l'iOS) : la saisie en
 * cours, où sont les autres sections, et où la changer.
 *
 * Le nom du mode se lit dans la table du site (`libellesModeSaisie`, `LIBELLE_MODE`, #229).
 */
@Composable
internal fun PhraseDeLaSaisie(forme: FormeDeSaisie, rangees: Int, repere: String) {
    val mode = TablesAffaires.libellesModeSaisie[forme.mode.code] ?: forme.mode.code
    Text(
        stringResource(if (rangees > 0) R.string.affaires_mode_phrase_rangees else R.string.affaires_mode_phrase, mode),
        Modifier.testTag(repere),
        style = MaterialTheme.typography.bodySmall,
        color = LocalCouleurs.current.textMuted,
    )
}

/**
 * « Plus de détails (n sections) » : les sections rangées, à leur rang, dans un groupe plié
 * (le `DisclosureGroup` de l'iOS). Plié, rien ne se perd — la saisie part telle quelle.
 * `ouvrirDOffice` : une section rangée porte autre chose que son défaut, le groupe s'ouvre.
 * Il s'ouvre en glissant (ease-out, 200 ms) ; sans animation si l'appareil les coupe.
 */
@Composable
internal fun PlusDeDetails(nombre: Int, ouvrirDOffice: Boolean, repere: String, contenu: @Composable ColumnScope.() -> Unit) {
    val c = LocalCouleurs.current
    var ouvert by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(ouvrirDOffice) { if (ouvrirDOffice) ouvert = true }
    val coupees = animationsCoupees()
    val rotation by animateFloatAsState(if (ouvert) 180f else 0f, if (coupees) snap() else tween(DUREE_MS, easing = Mouvement.sortie), label = "chevron")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = Mise.cible).testTag(repere)
                .toggleable(ouvert, role = Role.Button) { ouvert = it },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                pluralStringResource(R.plurals.affaires_plus_de_details, nombre, nombre),
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = c.primary,
            )
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = c.primary, modifier = Modifier.rotate(rotation))
        }
        AnimatedVisibility(
            ouvert,
            enter = if (coupees) fadeIn(snap()) else expandVertically(tween(DUREE_MS, easing = Mouvement.sortie)) + fadeIn(tween(DUREE_MS)),
            exit = if (coupees) fadeOut(snap()) else shrinkVertically(tween(DUREE_MS, easing = Mouvement.sortie)) + fadeOut(tween(DUREE_MS / 2)),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = contenu)
        }
    }
}

private const val DUREE_MS = 200
