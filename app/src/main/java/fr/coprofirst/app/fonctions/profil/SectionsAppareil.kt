// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.profil

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.commun.Apparence
import fr.coprofirst.app.commun.ConfirmationDeSortie
import fr.coprofirst.app.commun.GesteDeSortie
import fr.coprofirst.app.commun.GestesDeSortie

/**
 * « Apparence », comme l'iOS : Automatique / Clair / Sombre (`OPTIONS_THEME`), un réglage
 * de L'APPAREIL — comme le site, qui le retient dans le navigateur et pas dans le compte.
 * La couche commune le garde et l'applique à la racine.
 */
@Composable
fun SectionApparence(apparence: Apparence) {
    val choix by apparence.choix.collectAsStateWithLifecycle()
    SectionProfil(stringResource(R.string.profil_apparence), note = stringResource(R.string.profil_apparence_note)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().testTag("profil.theme")) {
            apparence.options.forEachIndexed { i, option ->
                SegmentedButton(
                    selected = option.valeur == choix,
                    onClick = { apparence.choisir(option.valeur) },
                    shape = SegmentedButtonDefaults.itemShape(i, apparence.options.size),
                    modifier = Modifier.heightIn(min = Mise.cible).testTag("profil.theme.${option.valeur}")
                        .semantics { stateDescription = option.description },
                ) { Text(option.libelle, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

/**
 * Les gestes de sortie, tout en bas comme l'iOS (`ProfilView.sectionSortie`) :
 * « Se déconnecter » en rouge, puis « Oublier toutes les résidences », discret — chacun
 * confirmé par la feuille commune (`ConfirmationDeSortie`).
 */
@Composable
fun SectionSortie(nomResidence: String, sortie: GestesDeSortie) {
    var geste by remember { mutableStateOf<GesteDeSortie?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionProfil(titre = null) {
            LigneAction(stringResource(R.string.se_deconnecter), "profil.deconnecter", detruit = true) {
                geste = GesteDeSortie.Deconnecter(nomResidence)
            }
        }
        Box(Modifier.padding(horizontal = Mise.marge + 4.dp)) {
            LigneAction(stringResource(R.string.sortie_oublier_bouton), "profil.oublierTout", discret = true) {
                geste = GesteDeSortie.OublierTout
            }
        }
    }
    ConfirmationDeSortie(geste, sortie) { geste = null }
}
