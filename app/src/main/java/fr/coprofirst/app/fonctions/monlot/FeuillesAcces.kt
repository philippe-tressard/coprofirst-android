// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.monlot

import android.content.res.Resources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.charte.Typo
import fr.coprofirst.app.commun.BoutonPlein
import fr.coprofirst.app.commun.Libelle
import fr.coprofirst.app.commun.MessageErreur
import fr.coprofirst.app.commun.acces.TypeAcces
import fr.coprofirst.app.commun.formats.FormatsDuSite
import fr.coprofirst.app.session.ErreurService
import kotlinx.coroutines.launch

/** La quantité qu'une demande admet (le pas de l'iOS). */
private val QUANTITES = 1..5

/** « Nouvelle demande d'accès » (`FormulairesAcces.svelte`) : type, un de SES lots, quantité, motif. */
@Composable
fun FeuilleDemander(lots: List<LotDuCompte>, service: ServiceMonLot, fermer: () -> Unit, fait: () -> Unit) {
    // Un seul lot : il est choisi d'avance, comme l'iOS.
    var type by rememberSaveable { mutableStateOf(TypeAcces.Vigik) }
    var lotId by rememberSaveable { mutableStateOf(lots.singleOrNull()?.id) }
    var quantite by rememberSaveable { mutableIntStateOf(1) }
    var motif by rememberSaveable { mutableStateOf("") }
    Feuille(R.string.monlot_nouvelle_demande, fermer, envoyer = {
        val lot = lotId ?: throw ErreurService.Refus(it.getString(R.string.monlot_choisir_un_lot), 0)
        service.demander(DemandeAcces(type, lot, quantite, motif))
        fait()
    }) {
        ChoixType(type) { type = it }
        Libelle(stringResource(R.string.monlot_lot_concerne))
        // Seuls SES lots : `/lots/mes-lots` ne sert jamais celui d'un voisin.
        Column(Modifier.selectableGroup().testTag(ReperesMonLot.DEMANDE_LOT)) {
            lots.forEach { lot -> Choix(stringResource(R.string.monlot_lot_numero, lot.numero), lotId == lot.id) { lotId = lot.id } }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.monlot_quantite), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = { quantite-- }, enabled = quantite > QUANTITES.first) {
                Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.monlot_moins))
            }
            Text(FormatsDuSite.nombre(quantite), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { quantite++ }, enabled = quantite < QUANTITES.last) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.monlot_plus))
            }
        }
        OutlinedTextField(
            value = motif,
            onValueChange = { motif = it },
            label = { Text(stringResource(R.string.monlot_motif)) },
            placeholder = { Text(stringResource(R.string.monlot_motif_indication)) },
            minLines = 2,
            maxLines = 6,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** « Déclarer un accès existant » : type (télécommande par défaut, comme le site) et code. */
@Composable
fun FeuilleDeclarer(service: ServiceMonLot, fermer: () -> Unit, fait: (importResolu: Boolean) -> Unit) {
    var type by rememberSaveable { mutableStateOf(TypeAcces.Telecommande) }
    var code by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    Feuille(R.string.monlot_declarer_existant, fermer, envoyer = {
        val saisi = code.trim().ifEmpty { throw ErreurService.Refus(it.getString(R.string.monlot_saisir_un_code), 0) }
        fait(service.declarer(type, saisi))
    }) { envoyer ->
        ChoixType(type) { type = it }
        OutlinedTextField(
            value = code,
            onValueChange = { code = it },
            label = { Text(stringResource(R.string.monlot_code_reference)) },
            placeholder = { Text(stringResource(R.string.monlot_code_indication)) },
            supportingText = { Text(stringResource(R.string.monlot_code_aide)) },
            singleLine = true,
            textStyle = TextStyle(fontFamily = FontFamily.Monospace),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { envoyer() }),
            modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag(ReperesMonLot.DECLARER_CODE),
        )
        LaunchedEffect(Unit) { focus.requestFocus() }
    }
}

/**
 * Une feuille de geste : titre, erreur (la phrase du serveur, ou celle de l'écran),
 * champs, puis « Enregistrer ». `envoyer` reçoit les ressources (pour la phrase d'un
 * champ manquant, dite en `Refus` de statut 0 : `MessageErreur` ne dit qu'une
 * `ErreurService`) et lève une `ErreurService` pour dire l'échec ; la feuille se ferme
 * sur un succès.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Feuille(
    titre: Int,
    fermer: () -> Unit,
    envoyer: suspend (Resources) -> Unit,
    champs: @Composable ColumnScope.(envoyer: () -> Unit) -> Unit,
) {
    val etat = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val portee = rememberCoroutineScope()
    val ressources = LocalResources.current
    var enCours by remember { mutableStateOf(false) }
    var erreur by remember { mutableStateOf<ErreurService?>(null) }
    val lancer: () -> Unit = {
        if (!enCours) {
            portee.launch {
                enCours = true
                try {
                    envoyer(ressources)
                    etat.hide()
                    fermer()
                } catch (e: ErreurService) {
                    erreur = e
                } finally {
                    enCours = false
                }
            }
        }
    }
    ModalBottomSheet(onDismissRequest = fermer, sheetState = etat) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Mise.marge).padding(bottom = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Mise.ecart),
        ) {
            Text(stringResource(titre), style = Typo.titre(), color = LocalCouleurs.current.text)
            erreur?.let { MessageErreur(it) }
            champs(lancer)
            BoutonPlein(R.string.monlot_enregistrer, Modifier.testTag(ReperesMonLot.ENREGISTRER), enCours = enCours, onClick = lancer)
        }
    }
}

@Composable
private fun ChoixType(type: TypeAcces, choisir: (TypeAcces) -> Unit) {
    Libelle(stringResource(R.string.monlot_type_d_acces))
    Column(Modifier.selectableGroup()) {
        TypeAcces.entries.forEach { t -> Choix(t.libelle, t == type) { choisir(t) } }
    }
}

@Composable
private fun Choix(libelle: String, retenu: Boolean, choisir: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = Mise.cible).selectable(retenu, role = Role.RadioButton, onClick = choisir),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = retenu, onClick = null)
        Text(libelle, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
    }
}
