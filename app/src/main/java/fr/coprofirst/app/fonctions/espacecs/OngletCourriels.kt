// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.espacecs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.commun.Carte
import fr.coprofirst.app.commun.Chargement
import fr.coprofirst.app.commun.EtatVide
import fr.coprofirst.app.commun.PastilleTeinte
import fr.coprofirst.app.commun.cliquable
import fr.coprofirst.app.commun.formats.FormatsDuSite
import fr.coprofirst.app.commun.onglets.ContexteOnglet
import fr.coprofirst.app.navigation.Destination

/**
 * « Courriels affaires » (onglet `courriels`, `OngletCourriels.svelte`), comme l'iOS
 * (`OngletCourriels`) : ce que la relève a fait de chaque message reçu à l'adresse des
 * affaires, et pourquoi — une pliure par message, du plus récent au plus ancien.
 * « Affaire #… » ouvre l'affaire rejointe (`lienAffaire`).
 */
@Composable
fun OngletCourriels(contexte: ContexteOnglet) {
    val service = remember(contexte.session) { ServiceCourrielsApi(contexte.session) }
    OngletCourriels(service, contexte.actualisation, contexte.ouvrir)
}

@Composable
fun OngletCourriels(service: ServiceCourriels, actualisation: Int, ouvrir: (Destination) -> Boolean = { false }) {
    Chargement(charger = { service.journal() }, cle = actualisation, squelette = { Journal(SQUELETTE, ouvrir) {} }) { journal, recharger ->
        Journal(journal, ouvrir, recharger)
    }
}

@Composable
private fun Journal(journal: JournalDeReleve, ouvrir: (Destination) -> Boolean, recharger: () -> Unit) {
    val c = LocalCouleurs.current
    Column(verticalArrangement = Arrangement.spacedBy(Mise.ecart)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.espacecs_courriels_aide, FormatsDuSite.nombre(journal.limite)),
                style = MaterialTheme.typography.bodySmall,
                color = c.textMuted,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = recharger, modifier = Modifier.testTag(ReperesEspaceCS.COURRIELS_RAFRAICHIR)) {
                Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.espacecs_courriels_rafraichir), tint = c.primary)
            }
        }
        if (journal.messages.isEmpty()) {
            EtatVide(stringResource(R.string.espacecs_courriels_aucun), Modifier.testTag(ReperesEspaceCS.COURRIELS_VIDE), icone = Icons.Outlined.Inbox)
        } else {
            Carte {
                journal.messages.forEachIndexed { i, message ->
                    if (i > 0) HorizontalDivider(color = c.border)
                    LigneCourriel(message, ouvrir)
                }
            }
        }
    }
}

/** Un message : son objet, son verdict et sa date ; dépliée, l'expéditeur, le motif et l'affaire rejointe. */
@Composable
private fun LigneCourriel(message: CourrielReleve, ouvrir: (Destination) -> Boolean) {
    val c = LocalCouleurs.current
    var ouvert by rememberSaveable(message.id) { mutableStateOf(false) }
    val angle by animateFloatAsState(if (ouvert) 90f else 0f, label = "pliure")
    val deplier = stringResource(R.string.espacecs_courriels_deplier)
    val replier = stringResource(R.string.espacecs_courriels_replier)
    Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .cliquable(if (ouvert) replier else deplier) { ouvert = !ouvert }
                .testTag(ReperesEspaceCS.courriel(message.id)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    message.objet.ifEmpty { stringResource(R.string.espacecs_courriels_sans_objet) },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = c.text,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                    PastilleTeinte(message.verdict, compacte = true)
                    Text(FormatsDuSite.dateHeureCourte(message.releveLe), style = MaterialTheme.typography.labelSmall, color = c.textMuted)
                }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = c.textMuted, modifier = Modifier.size(20.dp).rotate(angle))
        }
        AnimatedVisibility(ouvert) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val sousTitre = message.envoyeLe?.let {
                    stringResource(R.string.espacecs_courriels_envoye, message.expediteur, FormatsDuSite.dateHeureCourte(it))
                } ?: message.expediteur
                Text(sousTitre, style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                if (message.motif.isNotEmpty()) Text(message.motif, style = MaterialTheme.typography.bodyMedium, color = c.text)
                message.ticketId?.let { ticket ->
                    val lien = lienAffaire(ouvrir, ticket)
                    val action = stringResource(R.string.espacecs_ouvrir_affaire)
                    Row(
                        Modifier
                            .heightIn(min = Mise.cible)
                            .then(if (lien != null) Modifier.cliquable(action, lien) else Modifier)
                            .testTag(ReperesEspaceCS.courrielAffaire(message.id)),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (lien != null) Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, tint = c.primary, modifier = Modifier.size(18.dp))
                        Text(
                            stringResource(R.string.espacecs_courriels_affaire, message.affaire.orEmpty()),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (lien != null) c.primary else c.text,
                        )
                    }
                }
            }
        }
    }
}

private val SQUELETTE = JournalDeReleve(
    50,
    (1..4).map { CourrielReleve(-it, null, null, "Un expéditeur", "L'objet d'un message relevé", "ignore", "") },
)
