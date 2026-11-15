// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.espacecs

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.commun.BoutonPlein
import fr.coprofirst.app.commun.Carte
import fr.coprofirst.app.commun.Libelle
import fr.coprofirst.app.commun.MessageErreur
import fr.coprofirst.app.commun.Pastille
import fr.coprofirst.app.session.ErreurService
import kotlinx.coroutines.launch

/**
 * « Nouvelle annonce » (`FormulaireAnnonceHall.svelte`), comme l'iOS (`NouvelleAnnonceHall`) :
 * le titre, le message, le format et la Diffusion — puis l'affiche est créée (PDF rendu et
 * envois faits par le serveur). Sans photo ni assistant : ceux-là restent au site.
 *
 * ⚠️ Divergence déclarée : pas encore de choix du périmètre — le serveur pose le défaut,
 * comme l'iOS quand l'arbre des périmètres n'a pas pu être lu. Le choix attend la couche
 * commune (#83).
 */
@Composable
internal fun NouvelleAnnonceHall(service: ServiceAnnoncesHall, creee: (AnnonceHall) -> Unit) {
    val portee = rememberCoroutineScope()
    var titre by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf("") }
    var format by rememberSaveable { mutableStateOf(TablesEspaceCS.formatsAffiche.first().first) }
    var envoyerCS by rememberSaveable { mutableStateOf(false) }
    var envoyerSyndic by rememberSaveable { mutableStateOf(false) }
    var envoyerAuteur by rememberSaveable { mutableStateOf(false) }
    var envoi by remember { mutableStateOf(false) }
    var erreur by remember { mutableStateOf<ErreurService?>(null) }
    val saisie = SaisieAnnonceHall(titre, message, format, envoyerCS, envoyerSyndic, envoyerAuteur)

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Champ(R.string.espacecs_annonces_titre) {
            OutlinedTextField(
                value = titre,
                onValueChange = { titre = it },
                placeholder = { Text(stringResource(R.string.espacecs_annonces_titre_exemple)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag(ReperesEspaceCS.NOUVELLE_TITRE),
            )
        }
        Champ(R.string.espacecs_annonces_message) {
            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                placeholder = { Text(stringResource(R.string.espacecs_annonces_message_exemple)) },
                minLines = 4,
                maxLines = 12,
                modifier = Modifier.fillMaxWidth().testTag(ReperesEspaceCS.NOUVELLE_MESSAGE),
            )
        }
        Champ(R.string.espacecs_annonces_format, aide = R.string.espacecs_annonces_format_aide) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TablesEspaceCS.formatsAffiche.forEach { (code, libelle) ->
                    Pastille(libelle, format == code, Modifier.testTag(ReperesEspaceCS.nouvelleFormat(code))) { format = code }
                }
            }
        }
        Champ(R.string.espacecs_annonces_diffusion) {
            Carte {
                Case(R.string.espacecs_annonces_envoyer_cs, envoyerCS, ReperesEspaceCS.NOUVELLE_ENVOYER_CS) { envoyerCS = it }
                Case(R.string.espacecs_annonces_envoyer_syndic, envoyerSyndic, null) { envoyerSyndic = it }
                Case(R.string.espacecs_annonces_envoyer_auteur, envoyerAuteur, null) { envoyerAuteur = it }
            }
        }
        erreur?.let { MessageErreur(it) }
        BoutonPlein(
            R.string.espacecs_annonces_creer,
            Modifier.testTag(ReperesEspaceCS.NOUVELLE_CREER),
            actif = saisie.complete,
            enCours = envoi,
        ) {
            portee.launch {
                envoi = true
                erreur = null
                try {
                    val annonce = service.creer(saisie)
                    titre = ""
                    message = ""
                    format = TablesEspaceCS.formatsAffiche.first().first
                    envoyerCS = false
                    envoyerSyndic = false
                    envoyerAuteur = false
                    creee(annonce)
                } catch (e: ErreurService) {
                    erreur = e
                } finally {
                    envoi = false
                }
            }
        }
    }
}

/** Un champ : son libellé (petites capitales), le contrôle, puis son aide éventuelle. */
@Composable
private fun Champ(libelle: Int, aide: Int? = null, controle: @Composable () -> Unit) {
    val c = LocalCouleurs.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Libelle(stringResource(libelle))
        controle()
        aide?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = c.textMuted) }
    }
}

/** Une case de la Diffusion : décochée par défaut — la valeur par défaut d'un envoi est « ne pas envoyer ». */
@Composable
private fun Case(libelle: Int, coche: Boolean, repere: String?, changer: (Boolean) -> Unit) {
    val c = LocalCouleurs.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = Mise.cible).toggleable(coche, role = Role.Switch, onValueChange = changer)
            .then(if (repere != null) Modifier.testTag(repere) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(libelle), style = MaterialTheme.typography.bodyMedium, color = c.text, modifier = Modifier.weight(1f))
        Switch(checked = coche, onCheckedChange = null, colors = SwitchDefaults.colors(checkedTrackColor = c.primaryPlein))
    }
}
