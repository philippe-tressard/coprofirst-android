// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.commun.Carte
import fr.coprofirst.app.commun.Intertitre
import fr.coprofirst.app.commun.formats.FormatsDuSite

/**
 * « Accès : site ou application » (`CanauxAcces.svelte`, coprofirst#2726), comme l'iOS
 * (`CanauxAcces`, #127) : une ligne par canal qui a des vues — son libellé, ses vues et sa
 * part, ses utilisateurs —, la part en jauge, puis le pied. Tout est servi : rien ne se
 * recalcule ici.
 */
@Composable
internal fun CanauxAcces(canaux: List<TableauTelemetrie.PartCanal>) {
    val couleurs = LocalCouleurs.current
    // La colonne « Utilisateurs » du site : seulement si un canal en sert (Jour, Mois).
    val avecUtilisateurs = canaux.any { it.visiteurs != null }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Intertitre(stringResource(R.string.admin_telemetrie_acces), ecartAuDessus = 0.dp)
        Carte {
            canaux.forEachIndexed { rang, canal ->
                if (rang > 0) HorizontalDivider(color = couleurs.border)
                Ligne(canal, avecUtilisateurs)
            }
            piedAcces(canaux)?.let {
                Text(it, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = couleurs.textMuted)
            }
        }
    }
}

@Composable
private fun Ligne(canal: TableauTelemetrie.PartCanal, avecUtilisateurs: Boolean) {
    val couleurs = LocalCouleurs.current
    val vuesEtPart = stringResource(R.string.admin_telemetrie_vues_et_part, FormatsDuSite.nombre(canal.vues), FormatsDuSite.nombre(canal.part))
    Column(
        Modifier.fillMaxWidth().padding(vertical = 8.dp).semantics(mergeDescendants = true) {}.testTag(ReperesAdmin.acces(canal.canal)),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(canal.libelle, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = couleurs.text)
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(vuesEtPart, style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"), color = couleurs.text)
                if (avecUtilisateurs) {
                    Text(
                        canal.visiteurs?.let { stringResource(R.string.admin_telemetrie_utilisateurs, FormatsDuSite.nombre(it)) } ?: FormatsDuSite.TIRET,
                        style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                        color = couleurs.textMuted,
                    )
                }
            }
        }
        // La jauge redit la part, déjà lue : le lecteur d'écran la passe.
        LinearProgressIndicator(
            progress = { canal.part.coerceIn(0, 100) / 100f },
            modifier = Modifier.fillMaxWidth().clearAndSetSemantics {},
            color = couleurs.primary,
            trackColor = couleurs.border,
        )
    }
}

/** Le pied : un seul canal (d'où viennent toutes les vues), ou des utilisateurs qui ne s'additionnent pas. */
@Composable
private fun piedAcces(canaux: List<TableauTelemetrie.PartCanal>): String? = when {
    canaux.size == 1 -> stringResource(R.string.admin_telemetrie_un_seul_acces, canaux.single().libelle)
    canaux.any { it.visiteurs != null } -> stringResource(R.string.admin_telemetrie_utilisateurs_par_acces)
    else -> null
}
