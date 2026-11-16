// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.communaute

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.coprofirst.api.modeles.AnnonceRead
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.charte.Typo
import fr.coprofirst.app.commun.Pastille
import fr.coprofirst.app.commun.cliquable
import fr.coprofirst.app.commun.formats.FormatsDuSite
import fr.coprofirst.app.commun.onglets.ContexteOnglet
import fr.coprofirst.app.commun.pieces.PiecesJointes
import fr.coprofirst.app.commun.pieces.PiecesJointesVue
import fr.coprofirst.app.commun.texte.TexteDuSite
import fr.coprofirst.app.commun.texte.TexteRiche

/**
 * Une annonce ouverte, comme l'iOS (`DetailAnnonce`) : la description entière, les photos,
 * le contact, « Où en est cette annonce ? » pour son auteur, puis le fil des réponses.
 * Supprimer suit `peut_supprimer` SERVI (l'auteur ou le conseil). Corriger le texte ou les
 * photos se fait sur le site. « Signaler l'annonce » à qui ne l'a pas déposée.
 */
@Composable
fun DetailAnnonce(
    annonce: AnnonceRead?,
    contexte: ContexteOnglet,
    service: ServiceCommunaute,
    moi: Int,
    recharger: () -> Unit,
    fermer: () -> Unit,
) {
    // La suppression ferme l'annonce : sa phrase se dit sur la liste.
    val gestesDeLaListe = rememberGestes(contexte.avis)
    var aSupprimer by remember { mutableStateOf(false) }
    val signaler = annonce?.takeIf { it.estAuteur != true }?.let { FilCommunaute.Annonce(it.id) to R.string.annonces_signaler }
    DetailCommunaute(R.string.annonces_titre_detail, fermer, service, signaler, actions = {
        if (annonce?.peutSupprimer == true) {
            IconButton(onClick = { aSupprimer = true }, modifier = Modifier.testTag(ReperesCommunaute.ANNONCE_SUPPRIMER)) {
                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.annonces_supprimer), tint = LocalCouleurs.current.danger)
            }
        }
    }) { gestes ->
        if (annonce == null) {
            Etiquette(stringResource(R.string.annonces_introuvable))
            return@DetailCommunaute
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TypeAnnonce(annonce)
            Text(annonce.titre, style = Typo.titre(), color = LocalCouleurs.current.text, modifier = Modifier.semantics { heading() })
            EtiquettesAnnonce(annonce)
            Etiquette(FormatsDuSite.dateCourte(annonce.creeLe.toInstant()))
        }
        TexteRiche(annonce.description)
        PiecesJointesVue(remember(annonce.photos) { PiecesJointes(annonce.photos.orEmpty()) }, contexte.session)
        Contact(annonce)
        if (annonce.estAuteur == true) {
            Suivi(annonce) { statut ->
                gestes.agir(R.string.communaute_statut_mis_a_jour, apres = { if (it) recharger() }) {
                    service.changerStatutAnnonce(annonce.id, statut)
                    null
                }
            }
        }
        FilDuContenu(
            FilCommunaute.Annonce(annonce.id), annonce.reponses.orEmpty(), moi, service, gestes, recharger,
            invite = stringResource(R.string.annonces_question),
        )
    }
    if (aSupprimer && annonce != null) {
        AlertDialog(
            onDismissRequest = { aSupprimer = false },
            title = { Text(stringResource(R.string.annonces_supprimer_titre)) },
            text = { Text(stringResource(R.string.annonces_supprimer_message, annonce.titre)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        aSupprimer = false
                        gestesDeLaListe.agir(R.string.annonces_supprimee, apres = { reussi -> if (reussi) { fermer(); recharger() } }) {
                            service.supprimerAnnonce(annonce.id)
                            null
                        }
                    },
                    modifier = Modifier.testTag(ReperesCommunaute.ANNONCE_CONFIRMER),
                ) { Text(stringResource(R.string.annonces_supprimer), color = LocalCouleurs.current.danger) }
            },
            dismissButton = { TextButton(onClick = { aSupprimer = false }) { Text(stringResource(R.string.annuler)) } },
        )
    }
}

/** « 📬 Nom » : un courriel si l'auteur montre ses coordonnées, son nom sinon. */
@Composable
private fun Contact(annonce: AnnonceRead) {
    val auteur = auteurAnnonce(annonce) ?: return
    val couleurs = LocalCouleurs.current
    val navigateur = LocalUriHandler.current
    val lien = annonce.auteurEmail?.takeIf { it.isNotBlank() }?.let { TexteDuSite.lienSur("mailto:$it") }
    val modifier = if (lien != null) {
        Modifier.testTag(ReperesCommunaute.ANNONCE_CONTACT).cliquable(stringResource(R.string.annonces_contacter, auteur)) { navigateur.openUri(lien) }
    } else {
        Modifier
    }
    Row(modifier.heightIn(min = Mise.cible), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(if (lien != null) Icons.Outlined.Email else Icons.Outlined.Person, contentDescription = null, tint = if (lien != null) couleurs.primary else couleurs.textMuted)
        Text(auteur, style = MaterialTheme.typography.bodyLarge, color = if (lien != null) couleurs.primary else couleurs.textMuted)
    }
}

/** Pour l'auteur : l'état de l'annonce, en pastilles. */
@Composable
private fun Suivi(annonce: AnnonceRead, changer: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LibelleSection(R.string.annonces_ou_en_est)
        RangeeFiltres {
            TablesCommunaute.statutsAnnonce.forEach { (code, teinte) ->
                Pastille(teinte.libelle, annonce.statut.value == code) { if (code != annonce.statut.value) changer(code) }
            }
        }
    }
}
