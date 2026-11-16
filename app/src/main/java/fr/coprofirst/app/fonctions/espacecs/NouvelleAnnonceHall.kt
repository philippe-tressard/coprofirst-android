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
import fr.coprofirst.app.commun.perimetres.ArbreDesPerimetres
import fr.coprofirst.app.commun.perimetres.ChoixDePerimetres
import fr.coprofirst.app.commun.perimetres.ChoixPerimetres
import fr.coprofirst.app.commun.perimetres.ServicePerimetres
import fr.coprofirst.app.session.ErreurService
import kotlinx.coroutines.launch

/**
 * « Nouvelle annonce » (`FormulaireAnnonceHall.svelte`), comme l'iOS (`NouvelleAnnonceHall`) :
 * le titre, le message, le périmètre, le format et la Diffusion — puis l'affiche est créée
 * (PDF rendu et envois faits par le serveur). Sans photo ni assistant : ceux-là restent au site.
 *
 * Le périmètre se choisit dans l'arbre servi (`ChoixPerimetres`), le défaut du serveur
 * d'abord ; lu sans bloquer : sans arbre (échec, aucun périmètre), le choix ne paraît pas et
 * le serveur pose le défaut — comme l'iOS.
 */
@Composable
internal fun NouvelleAnnonceHall(service: ServiceAnnoncesHall, perimetres: ServicePerimetres?, creee: (AnnonceHall) -> Unit) {
    val portee = rememberCoroutineScope()
    var titre by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf("") }
    var format by rememberSaveable { mutableStateOf(TablesEspaceCS.formatsAffiche.first().first) }
    var envoyerCS by rememberSaveable { mutableStateOf(false) }
    var envoyerSyndic by rememberSaveable { mutableStateOf(false) }
    var envoyerAuteur by rememberSaveable { mutableStateOf(false) }
    var envoi by remember { mutableStateOf(false) }
    var erreur by remember { mutableStateOf<ErreurService?>(null) }
    var arbre by remember { mutableStateOf(ArbreDesPerimetres(emptyList())) }
    var choix by remember { mutableStateOf(ChoixDePerimetres()) }
    LaunchedEffect(perimetres) {
        // Non bloquant : sans périmètres, l'affiche prend celui que le serveur pose.
        val lu = try {
            ArbreDesPerimetres(perimetres?.perimetres().orEmpty())
        } catch (_: ErreurService) {
            ArbreDesPerimetres(emptyList())
        }
        // Le défaut AVANT l'arbre : les pastilles ne paraissent qu'avec l'arbre, un toucher ne
        // peut donc plus précéder le défaut et être écrasé par lui.
        if (choix.codes.isEmpty()) choix = choix.choisirDefaut(lu)
        arbre = lu
    }
    val saisie = SaisieAnnonceHall(titre, message, format, envoyerCS, envoyerSyndic, envoyerAuteur, choix.codes)

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
        if (arbre.noeuds.isNotEmpty()) {
            Champ(R.string.espacecs_annonces_perimetre, aide = R.string.espacecs_annonces_perimetre_aide) {
                ChoixPerimetres(arbre, choix, { choix = it }, repere = ReperesEspaceCS.NOUVELLE_PERIMETRE, service = perimetres)
            }
        }
        Champ(R.string.espacecs_annonces_format, aide = R.string.espacecs_annonces_format_aide) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).testTag(ReperesEspaceCS.NOUVELLE_FORMATS),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
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
                    choix = ChoixDePerimetres().choisirDefaut(arbre)
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
