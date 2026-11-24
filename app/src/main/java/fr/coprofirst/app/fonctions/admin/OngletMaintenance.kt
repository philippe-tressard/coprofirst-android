// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.commun.Carte
import fr.coprofirst.app.commun.Chargement
import fr.coprofirst.app.commun.Intertitre
import fr.coprofirst.app.commun.PastilleTeinte
import fr.coprofirst.app.commun.formats.FormatsDuSite
import fr.coprofirst.app.commun.onglets.ContexteOnglet
import fr.coprofirst.app.commun.texte.Separateur

/**
 * « Maintenance » (onglet `maintenance`), comme le site la partage et l'iOS la reprend :
 * la supervision technique — la santé des tâches planifiées — à l'OPÉRATEUR de la
 * plateforme ; le dernier export de la copropriété à l'ADMINISTRATION. Chacun ne voit que
 * ce que sa route lui sert : aucun nœud, aucune adresse n'est écrit dans l'app.
 */
@Composable
fun OngletMaintenance(contexte: ContexteOnglet, service: ServiceAdmin) {
    val couleurs = LocalCouleurs.current
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text(
            stringResource(if (contexte.droits.operateur) R.string.admin_maintenance_intro_operateur else R.string.admin_maintenance_intro_admin),
            style = MaterialTheme.typography.bodyMedium,
            color = couleurs.textMuted,
        )
        if (contexte.droits.operateur) SectionSanteTaches(contexte, service)
        if (contexte.droits.administrateur) SectionDernierExport(contexte, service)
        RenvoiAuSite(stringResource(R.string.admin_maintenance_sur_le_site), "maintenance", contexte.session.installation.url)
    }
}

/** « Santé des tâches planifiées » : une ligne par tâche, son statut jugé par le serveur, puis les échecs récents. */
@Composable
private fun SectionSanteTaches(contexte: ContexteOnglet, service: ServiceAdmin) {
    Column(verticalArrangement = Arrangement.spacedBy(Mise.ecart)) {
        Intertitre(stringResource(R.string.admin_maintenance_sante_titre), ecartAuDessus = 0.dp)
        Chargement(
            charger = { service.santeTaches() },
            cle = contexte.actualisation,
            squelette = { LigneTache(SanteTaches.Tache(stringResource(R.string.admin_maintenance_squelette_tache), "ok")) },
        ) { sante, _ ->
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                sante.taches.forEach { LigneTache(it) }
                if (sante.anomaliesRecentes.isNotEmpty()) Anomalies(sante)
            }
        }
    }
}

/** Une tâche : son nom, son statut, sa dernière exécution et, s'il en est un, le nœud qui décroche. */
@Composable
private fun LigneTache(tache: SanteTaches.Tache) {
    val couleurs = LocalCouleurs.current
    Carte(Modifier.semantics(mergeDescendants = true) {}.testTag(ReperesAdmin.tache(tache.tache))) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    LibellesMaintenance.tache(tache.tache),
                    style = MaterialTheme.typography.titleMedium,
                    color = couleurs.text,
                    modifier = Modifier.weight(1f),
                )
                PastilleTeinte(LibellesMaintenance.statut(tache.statut), compacte = true)
            }
            Text(
                listOfNotNull(stringResource(R.string.admin_maintenance_dernier_rapport, FormatsDuSite.dateHeure(tache.derniere)), tache.noeud)
                    .joinToString(Separateur.point),
                style = MaterialTheme.typography.bodySmall,
                color = couleurs.textMuted,
            )
            tache.noeudEnRetard?.let {
                PastilleTeinte(stringResource(R.string.admin_maintenance_en_retard, LibellesMaintenance.noeud(it)), "badge-orange", compacte = true)
            }
            LibellesMaintenance.aide(tache.statut)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = couleurs.textMuted)
            }
        }
    }
}

/** Les exécutions en échec de la fenêtre récente : la tâche, le nœud, la date, l'erreur servie. */
@Composable
private fun Anomalies(sante: SanteTaches) {
    val couleurs = LocalCouleurs.current
    Carte(Modifier.testTag(ReperesAdmin.ANOMALIES)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = couleurs.danger)
                Text(
                    stringResource(
                        R.string.admin_maintenance_anomalies,
                        FormatsDuSite.nombre(sante.anomaliesRecentes.size),
                        FormatsDuSite.nombre(sante.anomaliesFenetreJours),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = couleurs.danger,
                )
            }
            sante.anomaliesRecentes.forEach { anomalie ->
                Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        LibellesMaintenance.anomalie(anomalie),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = couleurs.text,
                    )
                    anomalie.erreur?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = couleurs.textMuted)
                    }
                }
            }
        }
    }
}

/** Le dernier export de la copropriété : l'archive, sa taille, ses lignes et ses fichiers, sa date — ou qu'il n'y en a aucun. */
@Composable
private fun SectionDernierExport(contexte: ContexteOnglet, service: ServiceAdmin) {
    Column(verticalArrangement = Arrangement.spacedBy(Mise.ecart)) {
        Intertitre(stringResource(R.string.admin_maintenance_export_titre), ecartAuDessus = 0.dp)
        Chargement(
            charger = { service.dernierExport() },
            cle = contexte.actualisation,
            squelette = { CarteExport(DernierExport(null, null, 0, 0, 0)) },
        ) { export, _ -> CarteExport(export) }
    }
}

@Composable
private fun CarteExport(export: DernierExport) {
    val couleurs = LocalCouleurs.current
    Carte(Modifier.testTag(ReperesAdmin.EXPORT)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(if (export.archive == null) Icons.Outlined.Inbox else Icons.Outlined.Archive, contentDescription = null, tint = couleurs.primary)
            Text(texteExport(export), style = MaterialTheme.typography.bodyMedium, color = couleurs.text)
        }
    }
}

@Composable
private fun texteExport(export: DernierExport): String {
    val archive = export.archive ?: return stringResource(R.string.admin_maintenance_aucun_export)
    val taille = FormatsDuSite.octets(export.octets)
    val lignes = FormatsDuSite.nombre(export.lignes)
    val fichiers = FormatsDuSite.nombre(export.fichiers)
    return export.creeLe?.let {
        stringResource(R.string.admin_maintenance_dernier_export_date, archive, taille, lignes, fichiers, FormatsDuSite.dateHeure(it))
    } ?: stringResource(R.string.admin_maintenance_dernier_export, archive, taille, lignes, fichiers)
}
