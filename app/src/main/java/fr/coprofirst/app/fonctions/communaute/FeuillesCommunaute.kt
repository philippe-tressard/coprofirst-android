// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.communaute

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.charte.Typo
import fr.coprofirst.app.commun.AvisDuGeste
import fr.coprofirst.app.commun.BoutonsFeuille
import fr.coprofirst.app.commun.MessageErreur
import fr.coprofirst.app.commun.rememberEnvoi

/**
 * Une feuille de saisie de la Communauté (`PiedFormulaire` de l'iOS) : le titre, l'erreur
 * du serveur en une phrase, les champs, puis « Enregistrer » (fermé tant que la saisie est
 * incomplète) et « Annuler ». Un envoi réussi ferme la feuille, dit `reussite` une fois
 * la feuille fermée, et `fait()` relit la liste ; un échec garde la saisie.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeuilleSaisie(
    @StringRes titre: Int,
    avis: AvisDuGeste,
    complete: Boolean,
    fermer: () -> Unit,
    envoyer: suspend () -> String,
    champs: @Composable ColumnScope.() -> Unit,
) {
    val etat = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val envoi = rememberEnvoi()
    val quitter = {
        avis.feuilleFermee()
        fermer()
    }
    ModalBottomSheet(onDismissRequest = { if (!envoi.enCours) quitter() }, sheetState = etat) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Mise.marge).padding(bottom = 16.dp)
                .navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(Mise.ecart),
        ) {
            Text(stringResource(titre), style = Typo.titre(), color = LocalCouleurs.current.text)
            envoi.erreur?.let { MessageErreur(it) }
            champs()
            BoutonsFeuille(envoi.enCours, ReperesCommunaute.ENREGISTRER, annuler = { if (!envoi.enCours) quitter() }, actif = complete) {
                envoi.lancer(effacer = true) {
                    avis.direApresFermeture(envoyer())
                    etat.hide()
                    quitter()
                }
            }
        }
    }
}

/** Un champ de saisie d'une feuille : son libellé, l'indication du site. */
@Composable
fun ChampCommunaute(
    @StringRes libelle: Int,
    valeur: String,
    changer: (String) -> Unit,
    @StringRes indication: Int,
    lignes: Int = 1,
) {
    OutlinedTextField(
        valeur,
        changer,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(libelle)) },
        placeholder = { Text(stringResource(indication)) },
        singleLine = lignes == 1,
        minLines = lignes,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
    )
}

/** Un interrupteur d'une feuille, touchable sur toute sa rangée. */
@Composable
fun InterrupteurCommunaute(@StringRes libelle: Int, valeur: Boolean, changer: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = Mise.cible).toggleable(valeur, role = Role.Switch, onValueChange = changer),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(libelle), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = valeur, onCheckedChange = null)
    }
}
