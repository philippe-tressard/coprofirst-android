// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.espacecs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.charte.Typo
import fr.coprofirst.app.commun.BoutonsFeuille
import fr.coprofirst.app.commun.MessageErreur
import fr.coprofirst.app.commun.PhraseAvis
import fr.coprofirst.app.session.ErreurService
import kotlinx.coroutines.launch

/** Rejeter un compte ou une demande : la question, puis « Rejeter » (en rouge) ou « Annuler ». */
@Composable
internal fun ConfirmationRejet(question: String, fermer: () -> Unit, rejeter: () -> Unit) {
    AlertDialog(
        onDismissRequest = fermer,
        title = { Text(question) },
        confirmButton = {
            TextButton(
                onClick = {
                    fermer()
                    rejeter()
                },
                modifier = Modifier.testTag(ReperesEspaceCS.REJET_CONFIRMER),
            ) { Text(stringResource(R.string.espacecs_rejeter), color = LocalCouleurs.current.danger) }
        },
        dismissButton = { TextButton(onClick = fermer) { Text(stringResource(R.string.espacecs_annuler)) } },
    )
}

/** Accepter une commande : les codes des badges remis, s'ils sont connus (le champ peut rester vide). */
@Composable
internal fun AccepterCommande(fermer: () -> Unit, accepter: (String) -> Unit) {
    var codes by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = fermer,
        title = { Text(stringResource(R.string.espacecs_accepter_commande)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Mise.ecart)) {
                Text(stringResource(R.string.espacecs_accepter_message))
                OutlinedTextField(
                    value = codes,
                    onValueChange = { codes = it },
                    label = { Text(stringResource(R.string.espacecs_codes_libelle)) },
                    placeholder = { Text(stringResource(R.string.espacecs_codes_indication)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth().testTag(ReperesEspaceCS.COMMANDE_CODES),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    fermer()
                    accepter(codes)
                },
                modifier = Modifier.testTag(ReperesEspaceCS.COMMANDE_ACCEPTER),
            ) { Text(stringResource(R.string.espacecs_accepter)) }
        },
        dismissButton = { TextButton(onClick = fermer) { Text(stringResource(R.string.espacecs_annuler)) } },
    )
}

/**
 * Approuver un compte (`ValidationCompte.svelte`), comme l'iOS (`ValidationCompteFeuille`) :
 * la case « Nouvel Arrivant », qui ouvre deux champs et déclenche l'accueil ; puis
 * « Approuver ». Sur un succès, la feuille se ferme et `fait` reçoit l'annonce du serveur ;
 * sur un refus, elle reste ouverte et dit la phrase du serveur.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FeuilleValidationCompte(
    compte: CompteEnAttente,
    valider: suspend (AccueilArrivant?) -> PhraseAvis?,
    fermer: () -> Unit,
    fait: (PhraseAvis?) -> Unit,
) {
    val etat = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val portee = rememberCoroutineScope()
    val c = LocalCouleurs.current
    var nouvelArrivant by rememberSaveable { mutableStateOf(false) }
    var batiment by rememberSaveable { mutableStateOf("") }
    var ancien by rememberSaveable { mutableStateOf("") }
    var envoi by remember { mutableStateOf(false) }
    var erreur by remember { mutableStateOf<ErreurService?>(null) }
    val envoyer: () -> Unit = {
        if (!envoi) {
            portee.launch {
                envoi = true
                try {
                    val annonce = valider(if (nouvelArrivant) AccueilArrivant(batiment, ancien) else null)
                    fait(annonce)
                    etat.hide()
                    fermer()
                } catch (e: ErreurService) {
                    erreur = e
                } finally {
                    envoi = false
                }
            }
        }
    }
    ModalBottomSheet(onDismissRequest = fermer, sheetState = etat) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Mise.marge).padding(bottom = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Mise.ecart),
        ) {
            Text(compte.nomAffiche, style = Typo.titre(), color = c.text)
            erreur?.let { MessageErreur(it) }
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Mise.cible)
                    .toggleable(nouvelArrivant, enabled = !envoi, role = Role.Switch) { nouvelArrivant = it }
                    .testTag(ReperesEspaceCS.NOUVEL_ARRIVANT),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Outlined.Home, contentDescription = null, tint = c.primary)
                Text(stringResource(R.string.espacecs_nouvel_arrivant), style = MaterialTheme.typography.titleMedium, color = c.text, modifier = Modifier.weight(1f))
                Switch(checked = nouvelArrivant, onCheckedChange = null, colors = SwitchDefaults.colors(checkedTrackColor = c.primaryPlein))
            }
            val aide = stringResource(R.string.espacecs_nouvel_arrivant_aide)
            val miseEnGarde = stringResource(R.string.espacecs_nouvel_arrivant_mise_en_garde)
            Text(
                buildAnnotatedString {
                    append(aide)
                    append(" ")
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(miseEnGarde) }
                },
                style = MaterialTheme.typography.bodySmall,
                color = c.textMuted,
            )
            AnimatedVisibility(nouvelArrivant) {
                Column(verticalArrangement = Arrangement.spacedBy(Mise.ecart)) {
                    Champ(batiment, { batiment = it }, R.string.espacecs_batiment_logement, R.string.espacecs_batiment_indication)
                    Champ(ancien, { ancien = it }, R.string.espacecs_ancien_resident, R.string.espacecs_ancien_resident_indication)
                }
            }
            BoutonsFeuille(
                envoi = envoi,
                repere = ReperesEspaceCS.VALIDATION_ENREGISTRER,
                annuler = fermer,
                libelle = R.string.espacecs_approuver,
                envoyer = envoyer,
            )
        }
    }
}

@Composable
private fun Champ(valeur: String, changer: (String) -> Unit, libelle: Int, indication: Int) {
    OutlinedTextField(
        value = valeur,
        onValueChange = changer,
        label = { Text(stringResource(libelle)) },
        placeholder = { Text(stringResource(indication)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}
