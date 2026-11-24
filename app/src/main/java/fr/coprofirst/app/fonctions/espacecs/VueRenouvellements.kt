// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.espacecs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.commun.AlerteChiffre
import fr.coprofirst.app.commun.Carte
import fr.coprofirst.app.commun.Chiffre
import fr.coprofirst.app.commun.EtatVide
import fr.coprofirst.app.commun.Libelle
import fr.coprofirst.app.commun.PastilleTeinte
import fr.coprofirst.app.commun.RangeeDeChiffres
import fr.coprofirst.app.commun.TitreDeBloc
import fr.coprofirst.app.commun.formats.FormatsDuSite
import fr.coprofirst.app.commun.texte.Separateur

/**
 * Reporting › Renouvellements (`VueRenouvellements.svelte`), comme l'iOS
 * (`VueRenouvellements`) : les trois compteurs, les contrats de prestataires à échéance,
 * puis les diagnostics réglementaires — tels que le serveur les sert et les ordonne. La
 * frise du site devient une liste : la date de fin et celle du préavis s'y lisent en clair.
 */
@Composable
internal fun VueRenouvellements(releve: Renouvellements) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        RangeeDeChiffres { m ->
            Chiffre(
                releve.contratsEnPreavis, stringResource(R.string.espacecs_renouvellements_preavis), m,
                alerte = if (releve.contratsEnPreavis > 0) AlerteChiffre.Danger else null, repere = ReperesEspaceCS.RENOUVELLEMENTS_PREAVIS,
            )
            Chiffre(releve.echeancesContratsAnnee, stringResource(R.string.espacecs_renouvellements_echeances_annee, releve.annee.toString()), m)
            Chiffre(
                releve.auditsAPlanifier, stringResource(R.string.espacecs_renouvellements_audits_a_planifier), m,
                alerte = if (releve.auditsAPlanifier > 0) AlerteChiffre.Danger else null,
            )
        }
        SectionContrats(releve)
        SectionDiagnostics(releve)
    }
}


@Composable
private fun SectionContrats(releve: Renouvellements) {
    val c = LocalCouleurs.current
    val annee = releve.annee.toString()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TitreDeBloc(
            stringResource(R.string.espacecs_renouvellements_contrats_titre, annee),
            intro = stringResource(R.string.espacecs_renouvellements_contrats_intro, annee, FormatsDuSite.nombre(releve.preavisMois)),
        )
        if (releve.contratsDeLAnnee.isEmpty()) {
            Text(stringResource(R.string.espacecs_renouvellements_aucune_echeance, annee), style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
        }
        releve.contratsDeLAnnee.forEach { LigneContrat(it, dansLAnnee = true) }
        Groupe(R.string.espacecs_renouvellements_echeances_futures, releve.contratsFuturs)
        Groupe(R.string.espacecs_renouvellements_dates_manquantes, releve.contratsSansDate)
    }
}

@Composable
private fun Groupe(titre: Int, contrats: List<ContratAEcheance>) {
    if (contrats.isEmpty()) return
    Libelle(stringResource(R.string.espacecs_renouvellements_groupe, stringResource(titre), contrats.size), Modifier.padding(top = 6.dp))
    contrats.forEach { LigneContrat(it, dansLAnnee = false) }
}

/** Un contrat : son libellé, son prestataire et sa note, son état, ses dates. */
@Composable
private fun LigneContrat(contrat: ContratAEcheance, dansLAnnee: Boolean) {
    val c = LocalCouleurs.current
    Carte(Modifier.testTag(ReperesEspaceCS.renouvellementContrat(contrat.id))) {
        Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(contrat.libelle, style = MaterialTheme.typography.titleMedium, color = c.text)
            val note = contrat.noteMoyenne?.let {
                stringResource(R.string.espacecs_renouvellements_note, FormatsDuSite.nombre(it), FormatsDuSite.nombre(contrat.nbNotations))
            }
            val numero = contrat.numeroContrat?.let { stringResource(R.string.espacecs_renouvellements_numero, it) }
            Text(listOfNotNull(contrat.prestataireNom, numero, note).joinToString(Separateur.point), style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (dansLAnnee && contrat.urgence == "preavis") {
                    PastilleTeinte(stringResource(R.string.espacecs_renouvellements_preavis_en_cours), "badge-orange")
                } else if (dansLAnnee) {
                    PastilleTeinte(stringResource(R.string.espacecs_renouvellements_actif), "badge-blue")
                }
                if (contrat.reconduit) PastilleTeinte(stringResource(R.string.espacecs_renouvellements_reconduit), "badge-purple")
            }
            if (contrat.dateFin != null) {
                val dates = "${FormatsDuSite.date(contrat.dateDebut)}${Separateur.fleche}${FormatsDuSite.date(contrat.dateFin)}"
                val periode = contrat.datePreavis?.let { stringResource(R.string.espacecs_renouvellements_preavis_des, dates, FormatsDuSite.date(it)) } ?: dates
                Text(periode, style = MaterialTheme.typography.bodySmall, color = c.textMuted)
            }
        }
    }
}

@Composable
private fun SectionDiagnostics(releve: Renouvellements) {
    val annee = releve.annee
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TitreDeBloc(
            stringResource(R.string.espacecs_renouvellements_diagnostics_titre, annee.toString(), (annee + 10).toString()),
            intro = stringResource(R.string.espacecs_renouvellements_diagnostics_intro),
        )
        if (releve.diagnostics.isEmpty()) {
            EtatVide(
                stringResource(R.string.espacecs_renouvellements_aucun_diagnostic),
                icone = Icons.Outlined.Verified,
                explication = stringResource(R.string.espacecs_renouvellements_aucun_diagnostic_detail),
            )
        }
        releve.diagnosticsParAnnee.forEach { (anneeGroupe, diagnostics) ->
            val courante = anneeGroupe == annee.toString()
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(anneeGroupe.ifEmpty { FormatsDuSite.TIRET }, style = MaterialTheme.typography.titleMedium, color = LocalCouleurs.current.text)
                PastilleTeinte(
                    pluralStringResource(R.plurals.espacecs_renouvellements_audits, diagnostics.size, diagnostics.size),
                    if (courante) "badge-orange" else "badge-blue",
                    compacte = true,
                )
            }
            diagnostics.forEach { LigneDiagnostic(it, annee) }
        }
        if (releve.diagnosticsPermanents.isNotEmpty()) {
            TitreDeBloc(
                stringResource(R.string.espacecs_renouvellements_permanent),
                Modifier.padding(top = 6.dp),
                intro = stringResource(R.string.espacecs_renouvellements_permanent_intro),
            )
            releve.diagnosticsPermanents.forEach { LigneDiagnostic(it, annee) }
        }
    }
}

/** Un diagnostic : son nom, son code et sa fréquence, ses dates, son état servi. */
@Composable
private fun LigneDiagnostic(diagnostic: DiagnosticAEcheanceLu, annee: Int) {
    val c = LocalCouleurs.current
    Carte(Modifier.testTag(ReperesEspaceCS.renouvellementDiagnostic(diagnostic.id))) {
        Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(diagnostic.nom, style = MaterialTheme.typography.titleMedium, color = c.text, modifier = Modifier.weight(1f))
                EtatDiagnostic(diagnostic, annee)
            }
            Text(listOfNotNull(diagnostic.code, diagnostic.frequence).joinToString(Separateur.point), style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
            Text(stringResource(R.string.espacecs_renouvellements_dernier_rapport, FormatsDuSite.date(diagnostic.dernierRapportLe)), style = MaterialTheme.typography.bodySmall, color = c.textMuted)
            if (!diagnostic.permanent) {
                Text(stringResource(R.string.espacecs_renouvellements_prochaine_echeance, FormatsDuSite.date(diagnostic.prochaineEcheance)), style = MaterialTheme.typography.bodySmall, color = c.textMuted)
            }
        }
    }
}

/** L'état que le serveur classe (`urgence_echeance`) : l'app ne compare aucune date. */
@Composable
private fun EtatDiagnostic(diagnostic: DiagnosticAEcheanceLu, annee: Int) {
    when {
        diagnostic.permanent -> PastilleTeinte(stringResource(R.string.espacecs_renouvellements_permanent_badge), "badge-green", compacte = true)
        diagnostic.urgence == "depasse" -> PastilleTeinte(stringResource(R.string.espacecs_renouvellements_depasse), "badge-red", compacte = true)
        diagnostic.urgence == "annee" -> PastilleTeinte(stringResource(R.string.espacecs_renouvellements_a_faire_en, annee.toString()), "badge-orange", compacte = true)
        diagnostic.prochaineEcheance != null -> PastilleTeinte(diagnostic.prochaineEcheance.take(4), "badge-blue", compacte = true)
    }
}
