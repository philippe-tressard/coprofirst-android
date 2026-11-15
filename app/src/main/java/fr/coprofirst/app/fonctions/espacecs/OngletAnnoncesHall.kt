// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.espacecs

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.commun.AvisDuGeste
import fr.coprofirst.app.commun.Carte
import fr.coprofirst.app.commun.Chargement
import fr.coprofirst.app.commun.EtatVide
import fr.coprofirst.app.commun.MessageErreur
import fr.coprofirst.app.commun.Pastille
import fr.coprofirst.app.commun.onglets.ContexteOnglet
import fr.coprofirst.app.session.ErreurService
import kotlinx.coroutines.launch

/** Les deux vues de l'onglet. */
enum class VueAnnoncesHall { Nouvelle, Archives }

/**
 * « Annonces Hall » (onglet `annonces-hall`, `OngletAnnoncesHall.svelte`), comme l'iOS
 * (`OngletAnnoncesHall`) : la création d'une affiche, et les archives des affiches déjà
 * produites, avec leurs gestes. Les photos, l'assistant, l'aperçu, la reprise d'un élément
 * du fil, WhatsApp et le PDF restent au site, et l'onglet le dit.
 */
@Composable
fun OngletAnnoncesHall(contexte: ContexteOnglet) {
    val service = remember(contexte.session) { ServiceAnnoncesHallApi(contexte.session) }
    OngletAnnoncesHall(service, contexte.avis, contexte.actualisation)
}

@Composable
fun OngletAnnoncesHall(service: ServiceAnnoncesHall, avis: AvisDuGeste, actualisation: Int) {
    var vue by rememberSaveable { mutableStateOf(VueAnnoncesHall.Nouvelle) }
    val ressources = LocalResources.current
    Column(Modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pastille(stringResource(R.string.espacecs_annonces_nouvelle), vue == VueAnnoncesHall.Nouvelle, Modifier.testTag(ReperesEspaceCS.ANNONCES_VUE_NOUVELLE)) {
                vue = VueAnnoncesHall.Nouvelle
            }
            Pastille(stringResource(R.string.espacecs_annonces_archives), vue == VueAnnoncesHall.Archives, Modifier.testTag(ReperesEspaceCS.ANNONCES_VUE_ARCHIVES)) {
                vue = VueAnnoncesHall.Archives
            }
        }
        when (vue) {
            VueAnnoncesHall.Nouvelle -> NouvelleAnnonceHall(service) { annonce ->
                avis.dire(creee(ressources, annonce))
                vue = VueAnnoncesHall.Archives
            }
            VueAnnoncesHall.Archives -> ArchivesAnnoncesHall(service, avis, actualisation)
        }
        Text(stringResource(R.string.espacecs_annonces_sur_le_site), style = MaterialTheme.typography.bodySmall, color = LocalCouleurs.current.textMuted)
    }
}

/** Le compte rendu d'une création, comme le site le dit. */
private fun creee(r: android.content.res.Resources, annonce: AnnonceHall): String {
    val n = annonce.destinataires.size
    return if (n > 0) {
        r.getQuantityString(R.plurals.espacecs_annonces_creee_envoyee, n, annonce.formatLabel, n)
    } else {
        r.getString(R.string.espacecs_annonces_creee_sans_envoi, annonce.formatLabel)
    }
}

/** Les affiches en cours puis les archivées, chacune dépliable, et leurs gestes. */
@Composable
private fun ArchivesAnnoncesHall(service: ServiceAnnoncesHall, avis: AvisDuGeste, actualisation: Int) {
    val portee = rememberCoroutineScope()
    val ressources = LocalResources.current
    var erreur by remember { mutableStateOf<ErreurService?>(null) }
    var aSupprimer by remember { mutableStateOf<AnnonceHall?>(null) }
    Chargement(charger = { service.toutes() }, cle = actualisation, squelette = { SqueletteAnnonces() }) { annonces, recharger ->
        fun faire(geste: GesteAnnonceHall, annonce: AnnonceHall) {
            erreur = null
            portee.launch {
                try {
                    val fait = when (geste) {
                        GesteAnnonceHall.Renvoyer -> {
                            service.renvoyer(annonce.id)
                            R.string.espacecs_annonces_renvoyee
                        }
                        GesteAnnonceHall.Archiver -> {
                            service.archiver(annonce.id, !annonce.archivee)
                            if (annonce.archivee) R.string.espacecs_annonces_restauree else R.string.espacecs_annonces_archivee_fait
                        }
                        GesteAnnonceHall.Supprimer -> {
                            service.supprimer(annonce.id)
                            R.string.espacecs_annonces_supprimee
                        }
                    }
                    avis.dire(ressources.getString(fait))
                    recharger()
                } catch (e: ErreurService) {
                    erreur = e
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(Mise.ecart)) {
            erreur?.let { MessageErreur(it) }
            if (annonces.isEmpty()) {
                EtatVide(
                    stringResource(R.string.espacecs_annonces_aucune),
                    Modifier.testTag(ReperesEspaceCS.ANNONCES_VIDE),
                    icone = Icons.Outlined.Description,
                    explication = stringResource(R.string.espacecs_annonces_aucune_detail),
                )
            }
            annonces.forEach { annonce ->
                CarteAnnonceHall(annonce) { g -> if (g == GesteAnnonceHall.Supprimer) aSupprimer = annonce else faire(g, annonce) }
            }
        }
        aSupprimer?.let { annonce ->
            AlertDialog(
                onDismissRequest = { aSupprimer = null },
                title = { Text(stringResource(R.string.espacecs_annonces_supprimer_question, annonce.titre)) },
                text = {
                    Text(
                        stringResource(R.string.espacecs_annonces_supprimer_message) +
                            if (annonce.destinataires.isEmpty()) "" else " " + stringResource(R.string.espacecs_annonces_supprimer_deja_envoyee),
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            aSupprimer = null
                            faire(GesteAnnonceHall.Supprimer, annonce)
                        },
                        modifier = Modifier.testTag(ReperesEspaceCS.ANNONCE_SUPPRIMER_CONFIRMER),
                    ) { Text(stringResource(R.string.espacecs_annonces_supprimer), color = LocalCouleurs.current.danger) }
                },
                dismissButton = { TextButton(onClick = { aSupprimer = null }) { Text(stringResource(R.string.espacecs_annuler)) } },
            )
        }
    }
}

@Composable
private fun SqueletteAnnonces() {
    Column(verticalArrangement = Arrangement.spacedBy(Mise.ecart)) {
        repeat(3) {
            Carte {
                Text("Une annonce de hall", style = MaterialTheme.typography.titleMedium)
                Text("L'aperçu de l'annonce arrive, sur deux lignes au plus.", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
