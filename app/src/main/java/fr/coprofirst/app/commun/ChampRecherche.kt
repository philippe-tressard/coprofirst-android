// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise

/**
 * Le champ de recherche d'une liste, DANS la page (il ne vaut que pour l'onglet qui le
 * porte), comme `ChampRecherche` de l'iOS : la loupe, l'`indication` de ce que l'on peut
 * y chercher (« Nom, métier, équipement… »), la croix qui l'efface. La règle de recherche
 * est `Recherche.contientTousLesMots(champs, texte)` (`commun/texte`), jamais une autre.
 * `repere` : l'identifiant du champ pour les tests d'interface.
 */
@Composable
fun ChampRecherche(
    texte: String,
    changer: (String) -> Unit,
    indication: String,
    repere: String,
    modifier: Modifier = Modifier,
) {
    val couleurs = LocalCouleurs.current
    val clavier = LocalSoftwareKeyboardController.current
    OutlinedTextField(
        value = texte,
        onValueChange = changer,
        placeholder = { Text(indication) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = couleurs.textMuted) },
        trailingIcon = if (texte.isEmpty()) null else {
            {
                IconButton(onClick = { changer("") }) {
                    Icon(Icons.Filled.Cancel, contentDescription = stringResource(R.string.recherche_effacer), tint = couleurs.textMuted)
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(Mise.rayon),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = couleurs.surface,
            unfocusedContainerColor = couleurs.surface,
            unfocusedBorderColor = couleurs.borderChamp,
        ),
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { clavier?.hide() }),
        modifier = modifier.fillMaxWidth().testTag(repere),
    )
}
