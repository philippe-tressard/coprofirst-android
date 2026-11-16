// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.fil

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.charte.Typo
import fr.coprofirst.app.commun.BoutonContour
import fr.coprofirst.app.commun.formats.FormatsDuSite
import fr.coprofirst.app.commun.pieces.PiecesJointesVue
import fr.coprofirst.app.commun.texte.TexteDuSite
import fr.coprofirst.app.commun.texte.TexteRiche
import fr.coprofirst.app.session.SessionOuverte

/** Le repère de la feuille de résumé, celui de l'iOS. */
const val REPERE_RESUME = "resume"

/**
 * Un élément sans écran natif — ou sans page sur le site : son résumé, jamais rien, comme
 * l'iOS (`ResumeFeuille`) : gouttière, rubrique et date, titre, texte riche, pièces jointes,
 * puis « Voir sur le site » quand le site a une page pour lui, sous l'adresse de la résidence
 * (ouverte par le navigateur).
 *
 * ⚠️ Divergence déclarée : la date s'écrit au format du site (`FormatsDuSite.dateHeure`,
 * « 15 nov. 2026, 09:30 ») ; l'iOS l'écrit en toutes lettres avec le jour de la semaine.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeuilleResume(element: ElementDuFil, session: SessionOuverte, fermer: () -> Unit) {
    val couleurs = LocalCouleurs.current
    val site = LocalUriHandler.current
    val lien = (element.cible as? CibleDuFil.Resume)?.lien?.let { DestinationDuFil.surLeSite(it, session.installation.url) }
    // Ouverte entière, comme toutes les feuilles : « Voir sur le site » ne reste pas sous le bord.
    val etat = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = fermer, sheetState = etat, containerColor = couleurs.bg) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Mise.marge).padding(bottom = Mise.marge)
                .testTag(REPERE_RESUME),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                VignetteDeNature(element)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        element.nature.libelle,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = element.nature.couleur(couleurs),
                    )
                    Text(FormatsDuSite.dateHeure(element.date), style = MaterialTheme.typography.labelMedium, color = couleurs.textMuted)
                }
            }
            Text(element.titre, style = Typo.titre(), color = couleurs.text, modifier = Modifier.semantics { heading() })
            element.detail?.takeIf { TexteDuSite.blocs(it).isNotEmpty() }?.let { TexteRiche(it) }
            PiecesJointesVue(element.pieces, session)
            if (lien != null) {
                BoutonContour(R.string.fil_voir_sur_le_site, Modifier.padding(top = 6.dp)) { site.openUri(lien) }
            }
        }
    }
}
