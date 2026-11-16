// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.navigation.EnTeteDeBarre
import kotlinx.coroutines.launch

/**
 * Une feuille PLEINE HAUTEUR qui porte un écran entier — les notifications de la cloche, la
 * fiche d'un sondage ouverte depuis le fil —, comme une feuille `.large` de l'iOS : son titre et
 * son `sousTitre` (`EnTeteDeBarre`), les `actions` de l'écran (sa propre barre), « OK » qui la
 * ferme (repère `repereOk`), puis le `contenu`, qui défile seul. Un balayage vers le bas la
 * ferme aussi.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeuillePleine(
    titre: String,
    repereOk: String,
    fermer: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    sousTitre: String? = null,
    contenu: @Composable BoxScope.() -> Unit,
) {
    val etat = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val portee = rememberCoroutineScope()
    val couleurs = LocalCouleurs.current
    ModalBottomSheet(onDismissRequest = fermer, sheetState = etat, containerColor = couleurs.bg) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(start = Mise.marge, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                EnTeteDeBarre(titre, sousTitre, Modifier.weight(1f))
                actions()
                TextButton(
                    onClick = { portee.launch { etat.hide() }.invokeOnCompletion { fermer() } },
                    modifier = Modifier.testTag(repereOk),
                ) {
                    Text(stringResource(R.string.feuille_ok), fontWeight = FontWeight.SemiBold)
                }
            }
            Box(Modifier.weight(1f), content = contenu)
        }
    }
}
