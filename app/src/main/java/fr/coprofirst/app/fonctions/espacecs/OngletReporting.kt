// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.espacecs

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.commun.AlerteChiffre
import fr.coprofirst.app.commun.Carte
import fr.coprofirst.app.commun.Chargement
import fr.coprofirst.app.commun.Chiffre
import fr.coprofirst.app.commun.EtatVide
import fr.coprofirst.app.commun.Pastille
import fr.coprofirst.app.commun.PastilleStatut
import fr.coprofirst.app.commun.PastilleTeinte
import fr.coprofirst.app.commun.RangeeDeChiffres
import fr.coprofirst.app.commun.formats.FormatsDuSite
import fr.coprofirst.app.commun.onglets.ContexteOnglet
import fr.coprofirst.app.navigation.Destination

/** Le mode de la vue Renouvellements, à la suite des modes du site. */
private const val RENOUVELLEMENTS = "renouvellements"

/**
 * « Reporting » (onglet `reporting`, `OngletReporting.svelte`), comme l'iOS
 * (`OngletReporting`) : le Suivi des affaires, en lecture — ses chiffres, ses deux
 * répartitions croisées et ses affaires, la plus ancienne en tête. Deux modes : les
 * affaires, ou les entretiens périodiques — la relance du syndic se compose sur le site ;
 * puis la vue Renouvellements. Ce que l'app laisse au site se dit sous la liste.
 * Toucher une affaire l'ouvre (`lienAffaire`).
 */
@Composable
fun OngletReporting(contexte: ContexteOnglet) {
    val service = remember(contexte.session) { ServiceReportingApi(contexte.session) }
    OngletReporting(service, contexte.actualisation, contexte.ouvrir)
}

@Composable
fun OngletReporting(service: ServiceReporting, actualisation: Int, ouvrir: (Destination) -> Boolean = { false }) {
    val renouvellements = stringResource(R.string.espacecs_renouvellements_titre)
    val modes = TablesEspaceCS.modesSuivi.filter { it.first != "relance" } + (RENOUVELLEMENTS to renouvellements)
    var mode by rememberSaveable { mutableStateOf(modes.first().first) }
    val c = LocalCouleurs.current
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            modes.forEach { (code, libelle) ->
                Pastille(libelle, mode == code, Modifier.testTag(ReperesEspaceCS.reportingMode(code))) { mode = code }
            }
        }
        if (mode == RENOUVELLEMENTS) {
            Chargement(charger = { service.renouvellements() }, cle = "$RENOUVELLEMENTS-$actualisation", squelette = {
                VueRenouvellements(SQUELETTE_RENOUVELLEMENTS)
            }) { releve, _ -> VueRenouvellements(releve) }
        } else {
            val releve = ReleveSuivi.duMode(mode)
            // Un autre mode, ou « tirer pour actualiser » : le relevé se relit.
            Chargement(charger = { service.suivi(releve) }, cle = "${releve.code}-$actualisation", squelette = {
                VueSuivi(SQUELETTE_SUIVI, entretien = false, ouvrir)
            }) { suivi, _ -> VueSuivi(suivi, releve == ReleveSuivi.Entretien, ouvrir) }
        }
        Text(stringResource(R.string.espacecs_reporting_sur_le_site), style = MaterialTheme.typography.bodySmall, color = c.textMuted)
    }
}

/** Un relevé : les chiffres, les répartitions, puis les affaires. */
@Composable
private fun VueSuivi(suivi: SuiviAffaires, entretien: Boolean, ouvrir: (Destination) -> Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(Mise.ecart)) {
        RangeeDeChiffres { m ->
            val nonFermees = if (entretien) R.plurals.espacecs_reporting_entretiens_non_clos else R.plurals.espacecs_reporting_affaires_non_fermees
            Chiffre(suivi.total, pluralStringResource(nonFermees, suivi.total), m, repere = ReperesEspaceCS.REPORTING_TOTAL)
            Chiffre(
                suivi.anciennes,
                stringResource(R.string.espacecs_reporting_anciennes, FormatsDuSite.nombre(suivi.delaiJours)),
                m,
                alerte = if (suivi.anciennes > 0) AlerteChiffre.Danger else null,
            )
            Chiffre(suivi.fusionnees, pluralStringResource(R.plurals.espacecs_reporting_fusionnees, suivi.fusionnees), m)
        }
        if (suivi.parCategorie.isNotEmpty()) RepartitionCroisee(stringResource(R.string.espacecs_reporting_par_categorie), suivi.parCategorie)
        if (suivi.parBatiment.isNotEmpty()) RepartitionCroisee(stringResource(R.string.espacecs_reporting_par_batiment), suivi.parBatiment)
        if (suivi.affaires.isEmpty()) {
            EtatVide(
                stringResource(if (entretien) R.string.espacecs_reporting_aucun_entretien else R.string.espacecs_reporting_aucune_affaire),
                Modifier.testTag(ReperesEspaceCS.REPORTING_VIDE),
                icone = Icons.Outlined.CheckCircle,
            )
        }
        suivi.affaires.forEach { LigneAffaireSuivie(it, entretien, lienAffaire(ouvrir, it.id)) }
    }
}

/**
 * Une affaire du suivi (`TableauAffaires.svelte`) : son âge teinté par son ancienneté, sa
 * date d'ouverture et son numéro, son titre, le signal « a évolué », l'aperçu (cinq lignes
 * au plus), son statut et sa catégorie — ou l'état de sa visite, en mode entretien.
 */
@Composable
private fun LigneAffaireSuivie(affaire: AffaireSuivie, entretien: Boolean, ouvrir: (() -> Unit)?) {
    val c = LocalCouleurs.current
    val ageLu = pluralStringResource(R.plurals.espacecs_reporting_age_lu, affaire.ageJours, FormatsDuSite.nombre(affaire.ageJours))
    Carte(Modifier.testTag(ReperesEspaceCS.reportingAffaire(affaire.id)), ouvrir = ouvrir, action = stringResource(R.string.espacecs_ouvrir_affaire)) {
        Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                PastilleTeinte(
                    stringResource(R.string.espacecs_reporting_age, FormatsDuSite.nombre(affaire.ageJours)),
                    affaire.teinteAge,
                    Modifier.semantics { contentDescription = ageLu },
                )
                Text(FormatsDuSite.date(affaire.ouverteLe), style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                Text(affaire.numero, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = c.textMuted)
            }
            Text(affaire.titre, style = MaterialTheme.typography.titleMedium, color = c.text)
            if (affaire.aEvolue && !entretien) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Filled.Notifications, contentDescription = null, tint = c.orangeTexte, modifier = Modifier.size(14.dp))
                    Text(affaire.evolution, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = c.orangeTexte)
                }
            }
            if (affaire.apercu.isNotEmpty()) Apercu(affaire)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                PastilleStatut(affaire.statut, affaire.libelleStatut)
                val visite = affaire.visite
                if (entretien && visite != null) {
                    PastilleTeinte(visite)
                } else {
                    Text(affaire.libelleCategorie, style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                }
            }
        }
    }
}

/** L'aperçu, marqué ✨ quand il est le résumé de l'assistant (dit au lecteur d'écran). */
@Composable
private fun Apercu(affaire: AffaireSuivie) {
    val c = LocalCouleurs.current
    val resume = stringResource(R.string.espacecs_reporting_resume_assistant)
    Row(
        Modifier.semantics { if (affaire.apercuIA) contentDescription = "$resume : ${affaire.apercu}" },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (affaire.apercuIA) Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = c.violet, modifier = Modifier.size(16.dp))
        Text(affaire.apercu, style = MaterialTheme.typography.bodyMedium, color = c.textMuted, maxLines = 5, overflow = TextOverflow.Ellipsis)
    }
}

private val SQUELETTE_SUIVI = SuiviAffaires(
    3, 1, 90, 0, emptyList(), emptyList(),
    (1..3).map { AffaireSuivie(-it, "TK-A00000", "Une affaire de la résidence", "ouvert", "Ouvert", "Catégorie", "2026-01-01", 10, "recente", "L'aperçu de l'affaire arrive.") },
)

private val SQUELETTE_RENOUVELLEMENTS = Renouvellements(2026, 3, 0, 0, 0, emptyList(), emptyList())
