// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.admin

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.commun.EtatVide
import fr.coprofirst.app.commun.MessageErreur
import fr.coprofirst.app.commun.Pastille
import fr.coprofirst.app.commun.formats.FormatsDuSite
import fr.coprofirst.app.commun.onglets.ContexteOnglet
import fr.coprofirst.app.session.ErreurService

/**
 * « Télémétrie » (onglet `telemetry`), comme l'iOS (`OngletTelemetrie`) : qui utilise
 * quoi, et quand — quatre vues, le filtre « sans le gestionnaire du site » quand le
 * serveur le propose, puis les nombres clés, le graphe, les pages et les utilisateurs
 * actifs. Le reste se lit sur le site.
 */
@Composable
fun OngletTelemetrie(contexte: ContexteOnglet, service: ServiceAdmin) {
    val etat = remember(service) { EtatTelemetrie(service) }
    var essai by remember { mutableIntStateOf(0) }
    // Une vue ou un filtre changé relit ; une lecture en cours est abandonnée pour la nouvelle.
    LaunchedEffect(etat, contexte.actualisation, etat.portee, etat.sansDemande, essai) { etat.charger() }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Vues(etat)
        etat.tableau?.filtre?.let { Filtre(etat, it) }
        Contenu(etat) { essai++ }
        RenvoiAuSite(stringResource(R.string.admin_telemetrie_sur_le_site), "telemetry", contexte.session.installation.url)
    }
}

/**
 * L'état de « Télémétrie » : la vue et le filtre DEMANDÉS, et le tableau lu pour eux. Un
 * échec écarte le tableau précédent : affiché sous une autre vue, il passerait pour elle.
 */
@Stable
class EtatTelemetrie(private val service: ServiceAdmin) {
    var portee by mutableStateOf(PorteeTelemetrie.Jour)
    var sansDemande by mutableStateOf(false)
    var tableau by mutableStateOf<TableauTelemetrie?>(null)
        private set

    /** La vue pour laquelle le tableau affiché a été lu (ses étiquettes en dépendent). */
    var porteeLue by mutableStateOf(PorteeTelemetrie.Jour)
        private set
    var erreur by mutableStateOf<ErreurService?>(null)
        private set

    /** Une relecture : l'ancien tableau reste, atténué. */
    var enCours by mutableStateOf(false)
        private set

    /** Le filtre tel qu'il s'affiche : la demande pendant la relecture, puis ce que le serveur a APPLIQUÉ. */
    val sansAffiche: Boolean get() = if (enCours) sansDemande else tableau?.filtre?.sansApplique ?: sansDemande

    suspend fun charger() {
        enCours = true
        try {
            val demandee = portee
            tableau = service.tableauTelemetrie(demandee, sansDemande)
            porteeLue = demandee
            erreur = null
        } catch (e: ErreurService) {
            tableau = null
            erreur = e
        } finally {
            enCours = false
        }
    }
}

/** Les quatre vues, en pastilles qui défilent de côté. */
@Composable
private fun Vues(etat: EtatTelemetrie) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PorteeTelemetrie.entries.forEach { portee ->
            Pastille(stringResource(portee.libelle), etat.portee == portee, Modifier.testTag(ReperesAdmin.vue(portee))) {
                etat.portee = portee
            }
        }
    }
}

/** « Sans le gestionnaire du site » (s'il change quelque chose), et ce que le filtre écarte. */
@Composable
private fun Filtre(etat: EtatTelemetrie, filtre: TableauTelemetrie.FiltreGestionnaire) {
    val couleurs = LocalCouleurs.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (filtre.propose) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Mise.cible)
                    .toggleable(etat.sansAffiche, role = Role.Switch) { etat.sansDemande = it }
                    .testTag(ReperesAdmin.FILTRE),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.admin_telemetrie_sans_gestionnaire),
                    style = MaterialTheme.typography.bodyLarge,
                    color = couleurs.text,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = etat.sansAffiche,
                    onCheckedChange = null,
                    colors = SwitchDefaults.colors(checkedTrackColor = couleurs.primaryPlein, checkedThumbColor = couleurs.surPlein),
                )
            }
        }
        if (!filtre.gestionnaireDesigne) Aide(stringResource(R.string.admin_telemetrie_aucun_gestionnaire))
        if (filtre.sansApplique) {
            val base = stringResource(R.string.admin_telemetrie_filtre_applique)
            val jusquAu = filtre.nonDistingueJusquAu?.let { stringResource(R.string.admin_telemetrie_filtre_jusqu_au, FormatsDuSite.date(it)) }
            Aide(listOfNotNull(base, jusquAu).joinToString(" "))
        }
    }
}

@Composable
private fun Contenu(etat: EtatTelemetrie, reessayer: () -> Unit) {
    val tableau = etat.tableau
    val erreur = etat.erreur
    val attenue by animateFloatAsState(if (etat.enCours) 0.5f else 1f, tween(200), label = "relecture")
    when {
        erreur != null -> MessageErreur(erreur, reessayer = reessayer)
        tableau == null -> {
            val annonce = stringResource(R.string.chargement)
            Box(Modifier.fillMaxWidth().padding(vertical = 32.dp).semantics { contentDescription = annonce }, contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = LocalCouleurs.current.primary)
            }
        }
        tableau.vide -> EtatVide(
            stringResource(R.string.admin_telemetrie_vide),
            icone = Icons.Outlined.BarChart,
            explication = stringResource(R.string.admin_telemetrie_vide_message),
        )
        else -> TableauDeFrequentation(tableau, etat.porteeLue, Modifier.alpha(attenue))
    }
}

@Composable
private fun Aide(texte: String) {
    Text(texte, style = MaterialTheme.typography.bodySmall, color = LocalCouleurs.current.textMuted)
}
