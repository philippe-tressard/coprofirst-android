// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.delegations

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.charte.Typo
import fr.coprofirst.app.commun.BoutonsFeuille
import fr.coprofirst.app.commun.Carte
import fr.coprofirst.app.commun.MessageErreur
import fr.coprofirst.app.commun.formats.FormatsDuSite
import fr.coprofirst.app.commun.rememberEnvoi
import fr.coprofirst.app.session.ErreurService
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** La date de fin proposée d'abord : dans trois mois, comme l'iOS. */
private const val FIN_PROPOSEE_JOURS = 90L

/**
 * « Nouvelle délégation aidant » (conseil syndical), comme `NouvelleDelegation` de l'iOS :
 * la personne aidée, son proche aidant, un motif et, au besoin, une date de fin. L'aidant
 * devra l'accepter. `creee` : la délégation est créée (la liste le dira) ; `fermer` suit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FeuilleNouvelleDelegation(service: ServiceDelegations, fermer: () -> Unit, creee: () -> Unit) {
    var saisie by remember { mutableStateOf(SaisieDelegation()) }
    var avecFin by rememberSaveable { mutableStateOf(false) }
    var fin by rememberSaveable { mutableStateOf(aujourdhui().plusDays(FIN_PROPOSEE_JOURS)) }
    var residents by remember { mutableStateOf<List<ResidentDelegable>>(emptyList()) }
    var residentsEnErreur by remember { mutableStateOf(false) }
    val envoi = rememberEnvoi()
    val etat = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { it != SheetValue.Hidden || !envoi.enCours })
    val portee = rememberCoroutineScope()
    val couleurs = LocalCouleurs.current
    val partir: () -> Unit = { portee.launch { etat.hide() }.invokeOnCompletion { fermer() } }

    LaunchedEffect(service) {
        try {
            residents = service.residentsDelegables().liables()
        } catch (_: ErreurService) {
            residentsEnErreur = true
        }
    }

    ModalBottomSheet(onDismissRequest = { if (!envoi.enCours) fermer() }, sheetState = etat, containerColor = couleurs.bg) {
        Column(
            Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = Mise.marge),
            verticalArrangement = Arrangement.spacedBy(Mise.ecart),
        ) {
            Text(
                stringResource(R.string.delegations_titre_formulaire),
                style = Typo.titre(),
                color = couleurs.text,
                modifier = Modifier.semantics { heading() },
            )
            if (residentsEnErreur) {
                Text(stringResource(R.string.delegations_residents_indisponibles), style = MaterialTheme.typography.bodyMedium, color = couleurs.danger)
            }
            ChoixResident(stringResource(R.string.delegations_mandant), residents, saisie.mandant, ReperesDelegations.MANDANT) {
                saisie = saisie.avecMandant(it)
            }
            ChoixResident(
                stringResource(R.string.delegations_proche_aidant),
                residents.filter { it.id != saisie.mandant },
                saisie.aidant,
                ReperesDelegations.AIDANT,
            ) { saisie = saisie.copy(aidant = it) }
            OutlinedTextField(
                value = saisie.motif,
                onValueChange = { saisie = saisie.copy(motif = it) },
                label = { Text(stringResource(R.string.delegations_motif)) },
                placeholder = { Text(stringResource(R.string.delegations_motif_exemple)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag(ReperesDelegations.MOTIF),
            )
            DateDeFin(avecFin, fin, basculer = { avecFin = it }) { fin = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = couleurs.textMuted, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.delegations_avertissement), style = MaterialTheme.typography.bodySmall, color = couleurs.textMuted)
            }
            AnimatedVisibility(visible = envoi.erreur != null) { envoi.erreur?.let { MessageErreur(it) } }
            BoutonsFeuille(
                envoi = envoi.enCours,
                repere = ReperesDelegations.ENREGISTRER,
                annuler = partir,
                modifier = Modifier.padding(bottom = 16.dp),
                actif = saisie.complete,
            ) {
                // `partir` lance la fermeture à part : elle part une fois l'envoi rendu.
                envoi.lancer(effacer = true) {
                    service.creer(saisie.copy(dateFin = fin.takeIf { avecFin }))
                    creee()
                    partir()
                }
            }
        }
    }
}

/** « Fixer une date de fin », puis la date, qui s'ouvre dans le calendrier ; sinon, ce que l'absence veut dire. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateDeFin(avecFin: Boolean, fin: LocalDate, basculer: (Boolean) -> Unit, choisir: (LocalDate) -> Unit) {
    val couleurs = LocalCouleurs.current
    var calendrier by remember { mutableStateOf(false) }
    Carte {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.delegations_avec_date_fin), color = couleurs.text, modifier = Modifier.weight(1f))
            Switch(checked = avecFin, onCheckedChange = basculer, modifier = Modifier.testTag(ReperesDelegations.AVEC_FIN))
        }
        if (avecFin) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.delegations_date_fin), color = couleurs.text, modifier = Modifier.weight(1f))
                TextButton(onClick = { calendrier = true }, modifier = Modifier.testTag(ReperesDelegations.DATE_FIN)) {
                    Text(FormatsDuSite.dateCourte(fin.toString()))
                }
            }
        }
    }
    if (!avecFin) {
        Text(stringResource(R.string.delegations_sans_date), style = MaterialTheme.typography.bodySmall, color = couleurs.textMuted)
    }
    if (calendrier) {
        // Le calendrier compte en minuit UTC : un jour s'y lit et s'y écrit à minuit UTC.
        val minimum = aujourdhui().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val etat = rememberDatePickerState(
            initialSelectedDateMillis = fin.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= minimum
            },
        )
        DatePickerDialog(
            onDismissRequest = { calendrier = false },
            confirmButton = {
                TextButton(onClick = {
                    etat.selectedDateMillis?.let { choisir(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    calendrier = false
                }) { Text(stringResource(R.string.delegations_ok)) }
            },
            dismissButton = { TextButton(onClick = { calendrier = false }) { Text(stringResource(R.string.annuler)) } },
        ) { DatePicker(etat) }
    }
}

/** Le jour qu'il est à Paris (le site ne propose pas de date passée). */
private fun aujourdhui(): LocalDate = LocalDate.now(FormatsDuSite.PARIS)
