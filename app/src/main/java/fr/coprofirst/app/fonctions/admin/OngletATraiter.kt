// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.commun.MessageErreur
import fr.coprofirst.app.commun.PhraseAvis
import fr.coprofirst.app.commun.Roles
import fr.coprofirst.app.commun.formats.FormatsDuSite
import fr.coprofirst.app.commun.onglets.ContexteOnglet
import fr.coprofirst.app.fonctions.espacecs.CarteDeFile
import fr.coprofirst.app.fonctions.espacecs.OngletComptesCS
import fr.coprofirst.app.session.ErreurService
import kotlinx.coroutines.launch

/**
 * « À traiter » (onglet `a_traiter`), comme l'iOS (`OngletATraiter`) : ce qui attend un
 * geste de l'administrateur — la file des comptes et des commandes d'accès, la MÊME que
 * celle du conseil (`OngletComptesCS` de l'Espace CS, mêmes routes, réemployée), puis
 * les demandes de modification de profil.
 */
@Composable
fun OngletATraiter(contexte: ContexteOnglet, service: ServiceAdmin) {
    Column(verticalArrangement = Arrangement.spacedBy(32.dp)) {
        OngletComptesCS(contexte)
        FileDemandesProfil(contexte, service)
    }
}

/** La file des demandes de profil : chargée, puis approuvée ou rejetée ligne à ligne. */
@Stable
class EtatDemandesProfil(private val service: ServiceAdmin) {
    var demandes by mutableStateOf<List<DemandeProfil>?>(null)
        private set
    var erreur by mutableStateOf<ErreurService?>(null)
        private set
    var enCours by mutableStateOf<Int?>(null)
        private set

    /** Une relecture qui échoue garde la file lue ; la première dit son erreur. */
    suspend fun charger() {
        try {
            demandes = service.demandesProfil()
            erreur = null
        } catch (e: ErreurService) {
            if (demandes == null) erreur = e
        }
    }

    /** Approuver, ou rejeter avec un motif : la demande quitte la file ; rend l'annonce du serveur. */
    suspend fun traiter(demande: DemandeProfil, approuver: Boolean, motif: String? = null): PhraseAvis {
        enCours = demande.id
        try {
            val annonce = service.traiterDemandeProfil(demande.id, approuver, motif)
            demandes = demandes?.filterNot { it.id == demande.id }
            return annonce
        } finally {
            enCours = null
        }
    }
}

@Composable
private fun FileDemandesProfil(contexte: ContexteOnglet, service: ServiceAdmin) {
    val etat = remember(service) { EtatDemandesProfil(service) }
    val portee = rememberCoroutineScope()
    var essai by remember { mutableIntStateOf(0) }
    var echec by remember { mutableStateOf<ErreurService?>(null) }
    var aRejeter by remember { mutableStateOf<DemandeProfil?>(null) }
    LaunchedEffect(etat, contexte.actualisation, essai) { etat.charger() }

    fun geste(demande: DemandeProfil, approuver: Boolean, motif: String? = null) {
        portee.launch {
            try {
                contexte.avis.dire(etat.traiter(demande, approuver, motif))
                echec = null
            } catch (e: ErreurService) {
                echec = e
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TitreSection(stringResource(R.string.admin_demandes_titre))
        val demandes = etat.demandes
        val erreur = etat.erreur
        when {
            erreur != null -> MessageErreur(erreur) { essai++ }
            demandes == null -> Text(stringResource(R.string.chargement), color = LocalCouleurs.current.textMuted)
            else -> {
                echec?.let { MessageErreur(it) }
                if (demandes.isEmpty()) {
                    Text(stringResource(R.string.admin_demandes_aucune), style = MaterialTheme.typography.bodyMedium, color = LocalCouleurs.current.textMuted)
                }
                demandes.forEach { demande ->
                    CarteDeFile(
                        ReperesAdmin.demande(demande.id),
                        etat.enCours == demande.id,
                        approuver = { geste(demande, approuver = true) },
                        rejeter = { aRejeter = demande },
                    ) { LigneDemandeProfil(demande) }
                }
            }
        }
    }
    aRejeter?.let { demande ->
        RejetDemande(demande, fermer = { aRejeter = null }) { motif ->
            aRejeter = null
            geste(demande, approuver = false, motif)
        }
    }
}

/** Ce qu'une demande change : le demandeur, son statut et son bâtiment actuels, ce qu'il souhaite, son motif, sa date. */
@Composable
private fun LigneDemandeProfil(demande: DemandeProfil) {
    val couleurs = LocalCouleurs.current
    Text(demande.utilisateurNom, style = MaterialTheme.typography.titleMedium, color = couleurs.text)
    demande.utilisateurEmail?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = couleurs.textMuted) }
    val actuel = listOfNotNull(demande.statutActuel?.let { Roles.statut(it).libelle }, demande.batimentActuel).joinToString(" — ")
    if (actuel.isNotEmpty()) Text(actuel, style = MaterialTheme.typography.bodyMedium, color = couleurs.textMuted)
    demande.statutSouhaite?.let { Souhait(stringResource(R.string.admin_demandes_type), Roles.statut(it).libelle) }
    demande.batimentNomSouhaite?.let { Souhait(stringResource(R.string.admin_demandes_batiment), it) }
    demande.motif?.takeIf { it.isNotBlank() }?.let {
        Text(it, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, color = couleurs.textMuted)
    }
    Text(FormatsDuSite.dateHeureCourte(demande.creeLe), style = MaterialTheme.typography.bodySmall, color = couleurs.textMuted)
}

/** « Type : Copropriétaire bailleur », la valeur souhaitée en gras, derrière une flèche. */
@Composable
private fun Souhait(libelle: String, valeur: String) {
    val couleurs = LocalCouleurs.current
    val modele = stringResource(R.string.admin_demandes_souhait, libelle, valeur)
    val debut = modele.lastIndexOf(valeur)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = couleurs.primary)
        Text(
            buildAnnotatedString {
                append(modele.substring(0, debut.coerceAtLeast(0)))
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = couleurs.text)) { append(valeur) }
            },
            style = MaterialTheme.typography.bodyMedium,
            color = couleurs.textMuted,
        )
    }
}

/** Rejeter une demande : la question, un motif facultatif, puis « Rejeter » (en rouge) ou « Annuler ». */
@Composable
private fun RejetDemande(demande: DemandeProfil, fermer: () -> Unit, rejeter: (String) -> Unit) {
    var motif by rememberSaveable(demande.id) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = fermer,
        title = { Text(stringResource(R.string.admin_demandes_rejeter, demande.utilisateurNom)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.admin_demandes_motif))
                OutlinedTextField(
                    motif,
                    { motif = it },
                    placeholder = { Text(stringResource(R.string.admin_demandes_motif_indication)) },
                    modifier = Modifier.fillMaxWidth().testTag(ReperesAdmin.DEMANDE_MOTIF),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { rejeter(motif) }, modifier = Modifier.testTag(ReperesAdmin.DEMANDE_REJETER)) {
                Text(stringResource(R.string.espacecs_rejeter), color = LocalCouleurs.current.danger)
            }
        },
        dismissButton = { TextButton(onClick = fermer) { Text(stringResource(R.string.espacecs_annuler)) } },
        containerColor = LocalCouleurs.current.surface,
    )
}
