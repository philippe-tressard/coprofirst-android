// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.delegations

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.charte.largeurDeLecture
import fr.coprofirst.app.commun.Actualisable
import fr.coprofirst.app.commun.AvisDuGeste
import fr.coprofirst.app.commun.AvisEphemere
import fr.coprofirst.app.commun.BoutonContour
import fr.coprofirst.app.commun.Chargement
import fr.coprofirst.app.commun.EtatVide
import fr.coprofirst.app.commun.MessageErreur
import fr.coprofirst.app.navigation.ContexteDeMenu
import fr.coprofirst.app.session.ErreurService
import kotlinx.coroutines.launch

/**
 * « Délégations aidant » (page `delegations` du site, #25), comme `DelegationsView` de
 * l'iOS : les délégations que le compte voit, les gestes que le serveur lui ouvre
 * (accepter, révoquer) et, pour le conseil syndical, la création d'une délégation.
 *
 * ⚠️ Lecture seule depuis coprofirst#1534 : l'aidant LIT ce que lit la personne aidée.
 * Rien ici ne fait agir quelqu'un au nom d'un autre.
 */
@Composable
fun EcranDelegations(contexte: ContexteDeMenu) {
    val service = remember(contexte.session) { ServiceDelegationsApi(contexte.session) }
    // `$isCS` du site (l'administrateur compris) : lui seul crée une délégation.
    EcranDelegations(service, conseil = contexte.droits.cs)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EcranDelegations(service: ServiceDelegations, conseil: Boolean) {
    var relecture by rememberSaveable { mutableIntStateOf(0) }
    var creation by rememberSaveable { mutableStateOf(false) }
    val avis = remember { AvisDuGeste() }
    val ressources = LocalResources.current
    AvisEphemere(avis, Modifier.fillMaxSize()) {
        Actualisable(enCours = false, actualiser = { relecture++ }, modifier = Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    Modifier.largeurDeLecture().fillMaxWidth().padding(horizontal = Mise.marge, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Dans la page, comme sur le site et l'iOS.
                    if (conseil) {
                        BoutonContour(R.string.delegations_nouvelle, Modifier.testTag(ReperesDelegations.NOUVELLE)) { creation = true }
                    }
                    Chargement(charger = { service.delegations() }, cle = relecture, squelette = { SqueletteDelegations() }) { liste, recharger ->
                        ListeDesDelegations(liste, service) { phrase ->
                            avis.dire(ressources.getString(phrase))
                            recharger()
                        }
                    }
                    // Sous la liste : ce qu'une délégation permet, sans repousser les délégations.
                    Text(TablesDelegations.DESCRIPTIF, style = MaterialTheme.typography.bodySmall, color = LocalCouleurs.current.textMuted)
                }
            }
        }
    }
    if (creation) {
        FeuilleNouvelleDelegation(
            service,
            fermer = {
                creation = false
                avis.feuilleFermee()
            },
            creee = {
                avis.direApresFermeture(ressources.getString(R.string.delegations_creee))
                relecture++
            },
        )
    }
}

/** Les cartes, l'erreur d'un geste au-dessus, et la confirmation de « Révoquer ». */
@Composable
private fun ListeDesDelegations(liste: List<Delegation>, service: ServiceDelegations, reussi: (Int) -> Unit) {
    var aRevoquer by remember { mutableStateOf<Delegation?>(null) }
    var enCours by remember { mutableStateOf<Int?>(null) }
    var erreur by remember { mutableStateOf<ErreurService?>(null) }
    val portee = rememberCoroutineScope()
    val geste: (Delegation, Int, suspend (Int) -> Delegation) -> Unit = { delegation, phrase, appel ->
        aRevoquer = null
        enCours = delegation.id
        erreur = null
        portee.launch {
            try {
                appel(delegation.id)
                reussi(phrase)
            } catch (e: ErreurService) {
                erreur = e
            } finally {
                enCours = null
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(Mise.ecart)) {
        AnimatedVisibility(visible = erreur != null) { erreur?.let { MessageErreur(it) } }
        if (liste.isEmpty()) {
            EtatVide(stringResource(R.string.delegations_aucune), Modifier.testTag(ReperesDelegations.VIDE), icone = Icons.Outlined.People)
        }
        liste.forEach { delegation ->
            CarteDelegation(
                delegation,
                enCours = enCours == delegation.id,
                accepter = { geste(delegation, R.string.delegations_acceptee, service::accepter) },
                revoquer = { aRevoquer = delegation },
            )
        }
    }
    aRevoquer?.let { delegation ->
        AlertDialog(
            onDismissRequest = { aRevoquer = null },
            title = { Text(stringResource(R.string.delegations_revoquer_question)) },
            confirmButton = {
                TextButton(
                    onClick = { geste(delegation, R.string.delegations_revoquee, service::revoquer) },
                    modifier = Modifier.testTag(ReperesDelegations.REVOQUER_CONFIRMER),
                ) {
                    Text(stringResource(R.string.delegations_revoquer), color = LocalCouleurs.current.danger, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = { TextButton(onClick = { aRevoquer = null }) { Text(stringResource(R.string.annuler)) } },
        )
    }
}

/** Trois cartes factices, à la forme d'une délégation, pendant la première lecture. */
@Composable
private fun SqueletteDelegations() {
    val factice = Delegation(
        id = 0,
        mandantNom = stringResource(R.string.delegations_squelette_aidee),
        aidantNom = stringResource(R.string.delegations_squelette_aidant),
        statut = "active",
        motif = stringResource(R.string.delegations_squelette_motif),
        dateDebut = "",
        dateFin = null,
    )
    Column(verticalArrangement = Arrangement.spacedBy(Mise.ecart)) {
        repeat(3) { CarteDelegation(factice, enCours = false, accepter = {}, revoquer = {}) }
    }
}
