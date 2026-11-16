// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.espacecs

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.charte.Typo
import fr.coprofirst.app.commun.BoutonsFeuille
import fr.coprofirst.app.commun.Libelle
import fr.coprofirst.app.commun.MessageErreur
import fr.coprofirst.app.commun.formats.FormatsDuSite
import fr.coprofirst.app.session.ErreurService
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Une feuille de l'annuaire, comme l'iOS (`FeuilleAnnuaire`) : son titre, la phrase du
 * serveur si l'enregistrement est refusé, ses champs, puis « Enregistrer » et « Annuler ».
 * Sur un succès, elle se ferme ; `complete` : faux tant que la fiche ne se nomme pas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FeuilleAnnuaire(
    titre: String,
    repere: String,
    fermer: () -> Unit,
    envoyer: suspend () -> Unit,
    complete: Boolean = true,
    champs: @Composable ColumnScope.() -> Unit,
) {
    val etat = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val portee = rememberCoroutineScope()
    var envoi by remember { mutableStateOf(false) }
    var erreur by remember { mutableStateOf<ErreurService?>(null) }
    ModalBottomSheet(onDismissRequest = fermer, sheetState = etat) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Mise.marge).padding(bottom = 16.dp)
                .navigationBarsPadding().testTag(repere),
            verticalArrangement = Arrangement.spacedBy(Mise.ecart),
        ) {
            Text(titre, style = Typo.titre(), color = LocalCouleurs.current.text)
            erreur?.let { MessageErreur(it) }
            champs()
            BoutonsFeuille(envoi = envoi, repere = "$repere.enregistrer", annuler = fermer, actif = complete) {
                if (!envoi) {
                    portee.launch {
                        envoi = true
                        try {
                            envoyer()
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
        }
    }
}

/**
 * L'en-tête du conseil : l'AG qui l'a élu, la communauté WhatsApp — et le président, choisi
 * parmi les membres : un seul, par construction (`surPresident` du site).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FeuilleEnteteConseil(conseil: ConseilCS, fermer: () -> Unit, enregistrer: suspend (ConseilCS) -> Unit) {
    var annee by rememberSaveable { mutableStateOf(conseil.agAnnee?.toString().orEmpty()) }
    var date by rememberSaveable { mutableStateOf(FormatsDuSite.lire(conseil.agDate)?.toEpochMilli()) }
    var choisirDate by remember { mutableStateOf(false) }
    var whatsapp by rememberSaveable { mutableStateOf(conseil.whatsappUrl.orEmpty()) }
    var president by rememberSaveable { mutableStateOf(conseil.membres.indexOfFirst { it.estPresident }.takeIf { it >= 0 }) }
    FeuilleAnnuaire(stringResource(R.string.annuaire_conseil), ReperesEspaceCS.FEUILLE_ENTETE_CONSEIL, fermer, envoyer = {
        enregistrer(
            conseil.avecPresident(president).copy(
                agAnnee = annee.trim().toIntOrNull(),
                agDate = date?.let { FormatsDuSite.jourIso(Instant.ofEpochMilli(it)) },
                whatsappUrl = whatsapp.trim().ifEmpty { null },
            ),
        )
    }) {
        Champ(annee, { annee = it.filter(Char::isDigit).take(4) }, R.string.espacecs_annuaire_vote_en_ag, R.string.espacecs_annuaire_annee_indication, KeyboardType.Number)
        Interrupteur(R.string.espacecs_annuaire_date_ag, date != null) { date = if (it) date ?: System.currentTimeMillis() else null }
        AnimatedVisibility(date != null) {
            TextButton(onClick = { choisirDate = true }, modifier = Modifier.heightIn(min = Mise.cible)) {
                Text(FormatsDuSite.date(date?.let(Instant::ofEpochMilli)))
            }
        }
        Champ(whatsapp, { whatsapp = it }, R.string.espacecs_annuaire_whatsapp, R.string.espacecs_annuaire_whatsapp_indication, KeyboardType.Uri)
        if (conseil.membres.isNotEmpty()) {
            Choix(R.string.annuaire_president, conseil.membres.map { it.nomAffiche }, president) { president = it }
        }
    }
    if (choisirDate) {
        val choix = rememberDatePickerState(initialSelectedDateMillis = date)
        DatePickerDialog(
            onDismissRequest = { choisirDate = false },
            confirmButton = {
                TextButton(onClick = {
                    choix.selectedDateMillis?.let { date = it }
                    choisirDate = false
                }) { Text(stringResource(R.string.espacecs_annuaire_ok)) }
            },
            dismissButton = { TextButton(onClick = { choisirDate = false }) { Text(stringResource(R.string.espacecs_annuler)) } },
        ) { DatePicker(choix) }
    }
}

/**
 * L'en-tête du syndic : son nom (désactivé quand le contrat de syndic fait foi), son adresse,
 * son espace client — et l'interlocuteur principal, un seul.
 */
@Composable
internal fun FeuilleEnteteSyndic(syndic: SyndicCS, fermer: () -> Unit, enregistrer: suspend (SyndicCS) -> Unit) {
    var nom by rememberSaveable { mutableStateOf(syndic.nomSyndic.orEmpty()) }
    var adresse by rememberSaveable { mutableStateOf(syndic.adresse.orEmpty()) }
    var site by rememberSaveable { mutableStateOf(syndic.siteWeb.orEmpty()) }
    var principal by rememberSaveable { mutableStateOf(syndic.membres.indexOfFirst { it.estPrincipal }.takeIf { it >= 0 }) }
    FeuilleAnnuaire(stringResource(R.string.annuaire_syndic), ReperesEspaceCS.FEUILLE_ENTETE_SYNDIC, fermer, envoyer = {
        enregistrer(syndic.avecPrincipal(principal).copy(nomSyndic = nom.trim(), adresse = adresse.trim(), siteWeb = site.trim().ifEmpty { null }))
    }) {
        Champ(nom, { nom = it }, R.string.espacecs_annuaire_nom_syndic, R.string.espacecs_annuaire_nom_syndic_indication, actif = !syndic.nomDuContrat)
        Text(
            stringResource(if (syndic.nomDuContrat) R.string.espacecs_annuaire_nom_du_contrat else R.string.espacecs_annuaire_nom_saisi),
            style = MaterialTheme.typography.bodySmall,
            color = LocalCouleurs.current.textMuted,
        )
        Champ(adresse, { adresse = it }, R.string.espacecs_annuaire_adresse, R.string.espacecs_annuaire_adresse_indication, lignes = 2..4)
        Champ(site, { site = it }, R.string.espacecs_annuaire_site_web, R.string.espacecs_annuaire_site_indication, KeyboardType.Uri)
        if (syndic.membres.isNotEmpty()) {
            Choix(R.string.annuaire_principal, syndic.membres.map { it.nomAffiche }, principal) { principal = it }
        }
    }
}

/** Un champ de saisie, son libellé et son indication. */
@Composable
internal fun Champ(
    valeur: String,
    changer: (String) -> Unit,
    libelle: Int,
    indication: Int,
    clavier: KeyboardType = KeyboardType.Text,
    actif: Boolean = true,
    lignes: IntRange = 1..1,
    repere: String? = null,
) {
    OutlinedTextField(
        value = valeur,
        onValueChange = changer,
        label = { Text(stringResource(libelle)) },
        placeholder = { Text(stringResource(indication)) },
        enabled = actif,
        singleLine = lignes.last == 1,
        minLines = lignes.first,
        maxLines = lignes.last,
        keyboardOptions = KeyboardOptions(keyboardType = clavier, autoCorrectEnabled = clavier == KeyboardType.Text),
        modifier = Modifier.fillMaxWidth().then(if (repere != null) Modifier.testTag(repere) else Modifier),
    )
}

/** Un interrupteur sur toute la ligne. */
@Composable
private fun Interrupteur(libelle: Int, coche: Boolean, changer: (Boolean) -> Unit) {
    val c = LocalCouleurs.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = Mise.cible).toggleable(coche, role = Role.Switch, onValueChange = changer),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(libelle), style = MaterialTheme.typography.bodyLarge, color = c.text, modifier = Modifier.weight(1f))
        Switch(checked = coche, onCheckedChange = null, colors = SwitchDefaults.colors(checkedTrackColor = c.primaryPlein))
    }
}

/** Un choix unique parmi les membres, « Aucun » d'abord (le président, l'interlocuteur principal). */
@Composable
private fun Choix(titre: Int, noms: List<String>, retenu: Int?, choisir: (Int?) -> Unit) {
    val c = LocalCouleurs.current
    Libelle(stringResource(titre))
    Column(Modifier.selectableGroup()) {
        (listOf(null to stringResource(R.string.espacecs_annuaire_aucun)) + noms.mapIndexed { i, n -> i to n }).forEach { (rang, nom) ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = Mise.cible).selectable(retenu == rang, role = Role.RadioButton) { choisir(rang) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = retenu == rang, onClick = null)
                Text(nom, style = MaterialTheme.typography.bodyLarge, color = c.text, modifier = Modifier.padding(start = 12.dp))
            }
        }
    }
}
