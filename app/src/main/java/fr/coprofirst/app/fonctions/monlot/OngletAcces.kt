// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.monlot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.SettingsRemote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.commun.Carte
import fr.coprofirst.app.commun.Chargement
import fr.coprofirst.app.commun.MessageErreur
import fr.coprofirst.app.commun.PastilleTeinte
import fr.coprofirst.app.commun.acces.LibellesAcces
import fr.coprofirst.app.commun.acces.TypeAcces
import fr.coprofirst.app.session.ErreurService
import kotlinx.coroutines.launch

/**
 * Les onglets « Mes badges d'accès (Vigik) » et « Télécommandes de parking »
 * (`OngletAcces.svelte`), comme l'iOS : ce que l'on détient, et « Signaler perdu » ; au
 * locataire, ce que son bailleur lui a confié ; au bailleur, ce qu'il a confié.
 */
@Composable
fun OngletAcces(type: TypeAcces, donnees: DonneesMonLot, service: ServiceMonLot, version: Int, annoncer: (Int) -> Unit) {
    Chargement(
        charger = { DonneesAcces.lire(service, donnees.droits) },
        cle = "${type.code}-$version",
        squelette = { SqueletteAcces() },
    ) { lues, recharger ->
        ContenuAcces(type, lues, donnees, service, annoncer, recharger)
    }
}

@Composable
private fun ContenuAcces(
    type: TypeAcces,
    lues: DonneesAcces,
    donnees: DonneesMonLot,
    service: ServiceMonLot,
    annoncer: (Int) -> Unit,
    recharger: () -> Unit,
) {
    var acces by remember(lues) { mutableStateOf(lues) }
    var aSignaler by remember { mutableStateOf<AccesDetenu?>(null) }
    var erreur by remember { mutableStateOf<ErreurService?>(null) }
    val portee = rememberCoroutineScope()
    val droits = donnees.droits
    val detenus = acces.detenus(type)
    Column(verticalArrangement = Arrangement.spacedBy(Mise.ecart)) {
        erreur?.let { MessageErreur(it) }
        // Un locataire sans objet ne voit pas la section ; tous les autres la voient.
        if (detenus.isNotEmpty() || !droits.locataire) {
            Intertitre(stringResource(if (type == TypeAcces.Vigik) R.string.monlot_badges_vigik else R.string.monlot_telecommandes))
            if (detenus.isEmpty()) {
                NoteMonLot(
                    stringResource(if (type == TypeAcces.Vigik) R.string.monlot_aucun_badge else R.string.monlot_aucune_telecommande),
                    icone = if (type == TypeAcces.Vigik) Icons.Outlined.Sell else Icons.Outlined.SettingsRemote,
                )
            } else {
                Carte {
                    detenus.forEachIndexed { rang, lu ->
                        if (rang > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp), color = LocalCouleurs.current.border)
                        LigneAcces(lu) { aSignaler = lu }
                    }
                }
            }
        }
        if (droits.locataire) AccesConfies(acces.recus)
        if (droits.bailleur && donnees.baux.isNotEmpty()) {
            VueParLocataire(donnees.bauxActifs, acces.vigiks + acces.telecommandes, service, annoncer, recharger)
        }
    }
    aSignaler?.let { lu ->
        AlertDialog(
            onDismissRequest = { aSignaler = null },
            title = { Text(stringResource(R.string.monlot_signaler_perdu_question)) },
            text = { Text(lu.code, fontFamily = FontFamily.Monospace) },
            confirmButton = {
                TextButton(
                    onClick = {
                        aSignaler = null
                        portee.launch {
                            try {
                                service.signalerPerdu(lu)
                                acces = acces.marquerPerdu(lu)
                                erreur = null
                                annoncer(R.string.monlot_signalement_enregistre)
                            } catch (e: ErreurService) {
                                erreur = e
                            }
                        }
                    },
                    modifier = Modifier.testTag(ReperesMonLot.PERDU_CONFIRMER),
                ) { Text(stringResource(R.string.monlot_signaler_perdu), color = LocalCouleurs.current.danger) }
            },
            dismissButton = { TextButton(onClick = { aSignaler = null }) { Text(stringResource(R.string.monlot_annuler)) } },
        )
    }
}

/** Une ligne : le code en chasse fixe, son statut, et « Signaler perdu » s'il est actif. */
@Composable
private fun LigneAcces(acces: AccesDetenu, signaler: () -> Unit) {
    val couleurs = LocalCouleurs.current
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.Center,
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        SelectionContainer { Text(acces.code, fontFamily = FontFamily.Monospace, color = couleurs.text) }
        PastilleTeinte(LibellesAcces.statutAcces(acces.statut))
        Spacer(Modifier.weight(1f))
        if (acces.statut == DonneesAcces.STATUT_ACTIF) {
            TextButton(onClick = signaler, modifier = Modifier.heightIn(min = Mise.cible).testTag(ReperesMonLot.perdu(acces.id))) {
                Text(stringResource(R.string.monlot_signaler_perdu), color = couleurs.danger, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** « Accès confiés par votre bailleur » (`AccesConnexes.svelte`) — sous les deux onglets. */
@Composable
private fun AccesConfies(recus: List<AccesDetenu>) {
    val couleurs = LocalCouleurs.current
    Intertitre(stringResource(R.string.monlot_acces_confies))
    if (recus.isEmpty()) {
        NoteMonLot(stringResource(R.string.monlot_aucun_acces_confie))
        return
    }
    Text(stringResource(R.string.monlot_acces_confies_intro), style = MaterialTheme.typography.bodyMedium, color = couleurs.textMuted)
    Carte {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            recus.forEach { lu ->
                Row(Modifier.semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(lu.code, fontFamily = FontFamily.Monospace, color = couleurs.text)
                    Text(LibellesAcces.typeAcces(lu.type), style = MaterialTheme.typography.bodyMedium, color = couleurs.textMuted, modifier = Modifier.weight(1f))
                    PastilleTeinte(LibellesAcces.statutAcces(lu.statut), compacte = true)
                }
            }
        }
    }
}

/** Le squelette : trois lignes grisées. */
@Composable
private fun SqueletteAcces() {
    Column(verticalArrangement = Arrangement.spacedBy(Mise.ecart)) {
        Intertitre(stringResource(R.string.monlot_badges_vigik))
        Carte {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                repeat(3) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("XXXXXXXX", fontFamily = FontFamily.Monospace)
                        Text("Actif")
                    }
                }
            }
        }
    }
}
