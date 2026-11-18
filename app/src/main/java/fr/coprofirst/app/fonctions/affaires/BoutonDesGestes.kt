// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.affaires

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.commun.gesteEnLigne

/**
 * Le « ⋯ » des gestes d'un élément de la fiche, comme l'iOS (`BoutonDesGestes`) : une entrée
 * de la suite, un message du fil (#281). Visible à droite de l'élément — un appui long seul
 * serait un geste caché —, et les mêmes gestes en actions du lecteur d'écran. Grisé en
 * consultation hors ligne, sans actions du lecteur, qui passeraient outre. Rien sans geste.
 *
 * `repere` : celui du bouton ; `repereGeste` : celui de chaque geste du menu (ceux de l'iOS).
 */
@Composable
internal fun BoutonDesGestes(
    offerts: List<GesteSuite>,
    repere: String,
    repereGeste: (GesteSuite) -> String,
    description: String,
    libelle: (GesteSuite) -> String,
    choisir: (GesteSuite) -> Unit,
) {
    if (offerts.isEmpty()) return
    var menu by remember { mutableStateOf(false) }
    val geste = gesteEnLigne()
    val actions = if (!geste.actif) emptyList() else offerts.map { g -> CustomAccessibilityAction(libelle(g)) { choisir(g); true } }
    val couleurs = LocalCouleurs.current
    Box {
        IconButton({ menu = true }, geste.modifier.size(32.dp).semantics { customActions = actions }.testTag(repere), enabled = geste.actif) {
            Icon(Icons.Outlined.MoreHoriz, contentDescription = description, tint = if (geste.actif) couleurs.primary else couleurs.textMuted)
        }
        DropdownMenu(menu, { menu = false }) {
            offerts.forEach { g ->
                val retire = g == GesteSuite.Supprimer
                DropdownMenuItem(
                    text = { Text(libelle(g), color = if (retire) couleurs.danger else couleurs.text) },
                    leadingIcon = { Icon(if (retire) Icons.Outlined.Delete else Icons.Outlined.Edit, contentDescription = null) },
                    onClick = { menu = false; choisir(g) },
                    modifier = Modifier.testTag(repereGeste(g)),
                )
            }
        }
    }
}
