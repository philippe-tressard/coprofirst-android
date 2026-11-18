// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.residence

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.commun.MessageErreur
import fr.coprofirst.app.commun.SignalementAuConseil
import fr.coprofirst.app.commun.gesteEnLigne
import fr.coprofirst.app.session.ErreurService
import kotlinx.coroutines.launch

/** Le repère du geste, celui de l'iOS. */
const val REPERE_SIGNALER_AVIS = "reglement.signaler"

/**
 * 🚩 « Signaler la réponse de l'assistant » (#282, coprofirst#2861 « 2a »), comme l'iOS : les
 * magasins exigent qu'une réponse de l'IA se signale sans quitter l'app. Sous la mention de
 * l'IA, là où l'on doute de l'avis ; le motif, puis l'envoi (cible `question_reglement`).
 *
 * ⚠️ Divergence déclarée : l'iOS confirme par son avis éphémère ; l'avis s'ouvre ici dans une
 * feuille, où une phrase dite par l'écran passerait derrière — la confirmation (ou la phrase
 * du serveur) remplace le bouton, sur place.
 */
@Composable
internal fun SignalerLaReponse(question: QuestionReglement, signaler: suspend (motif: String) -> Unit) {
    val couleurs = LocalCouleurs.current
    val portee = rememberCoroutineScope()
    var ouverte by remember(question.id) { mutableStateOf(false) }
    var transmis by remember(question.id) { mutableStateOf(false) }
    var erreur by remember(question.id) { mutableStateOf<ErreurService?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        erreur?.let { MessageErreur(it) }
        if (transmis) {
            Text(stringResource(R.string.signalement_transmis), style = MaterialTheme.typography.bodySmall, color = couleurs.textMuted)
        } else {
            val geste = gesteEnLigne()
            TextButton(
                onClick = { ouverte = true },
                enabled = geste.actif,
                modifier = geste.modifier.heightIn(min = Mise.cible).testTag(REPERE_SIGNALER_AVIS),
            ) {
                Icon(Icons.Outlined.Flag, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(" " + stringResource(R.string.avis_signaler), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    SignalementAuConseil(
        question.takeIf { ouverte },
        envoyer = { _, motif ->
            portee.launch {
                erreur = null
                try {
                    signaler(motif)
                    transmis = true
                } catch (e: ErreurService) {
                    erreur = e
                }
            }
        },
        fermer = { ouverte = false },
    )
}
