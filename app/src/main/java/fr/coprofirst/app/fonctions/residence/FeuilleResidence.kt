// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.residence

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.charte.Typo
import kotlinx.coroutines.launch

/**
 * Une page ouverte par-dessus « Ma résidence » (l'avis d'une question, l'annuaire du
 * syndic) : son titre, « OK » qui la ferme, puis son contenu. `defile` : le contenu défile
 * dans la feuille (un écran qui défile lui-même passe `false`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeuilleResidence(titre: String, fermer: () -> Unit, defile: Boolean = true, contenu: @Composable () -> Unit) {
    val etat = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val portee = rememberCoroutineScope()
    val couleurs = LocalCouleurs.current
    ModalBottomSheet(onDismissRequest = fermer, sheetState = etat, containerColor = couleurs.bg) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(start = Mise.marge, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    titre, style = Typo.titre(), color = couleurs.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                TextButton(
                    onClick = { portee.launch { etat.hide() }.invokeOnCompletion { fermer() } },
                    modifier = Modifier.testTag(ReperesResidence.FERMER),
                ) {
                    Text(stringResource(R.string.avis_fermer), fontWeight = FontWeight.SemiBold)
                }
            }
            Box(Modifier.weight(1f)) {
                if (defile) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Mise.marge, vertical = 16.dp)) {
                        contenu()
                    }
                } else {
                    contenu()
                }
            }
        }
    }
}
