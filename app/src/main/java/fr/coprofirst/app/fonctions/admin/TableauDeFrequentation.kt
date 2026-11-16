// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
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
import fr.coprofirst.app.charte.Typo
import fr.coprofirst.app.commun.Carte
import fr.coprofirst.app.commun.Roles
import fr.coprofirst.app.commun.formats.FormatsDuSite
import fr.coprofirst.app.commun.texte.Separateur

/** La largeur minimale d'un nombre clé : deux par ligne sur un téléphone, un seul en très grand texte. */
private val LARGEUR_INDICATEUR = 150.dp

/**
 * « Indicateurs et fréquentation » (`FrequentationTelemetrie.svelte`), comme l'iOS
 * (`TableauDeFrequentation`) : les nombres clés de la vue, ses records, le graphe titré
 * par le serveur, les pages et les utilisateurs.
 */
@Composable
fun TableauDeFrequentation(tableau: TableauTelemetrie, portee: PorteeTelemetrie, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Indicateurs(LecturesTelemetrie.indicateurs(tableau.kpi, portee))
        Carte(Modifier.testTag(ReperesAdmin.GRAPHE)) {
            Text(tableau.titreGraphe, style = MaterialTheme.typography.titleMedium, color = LocalCouleurs.current.text)
            Spacer(Modifier.height(12.dp))
            GrapheBatons(tableau.graphe, portee)
        }
        if (tableau.canaux.isNotEmpty()) CanauxAcces(tableau.canaux)
        if (tableau.pages.isNotEmpty()) Pages(tableau.pages, accesParPage = tableau.plusieursCanauxParPage)
        if (tableau.utilisateurs.isNotEmpty()) Utilisateurs(tableau.utilisateurs)
    }
}

/** Les nombres clés en grille : autant de colonnes que la largeur en admet. */
@Composable
private fun Indicateurs(indicateurs: List<Indicateur>) {
    BoxWithConstraints(Modifier.fillMaxWidth().testTag(ReperesAdmin.INDICATEURS)) {
        val colonnes = (maxWidth / LARGEUR_INDICATEUR).toInt().coerceAtLeast(1)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            indicateurs.chunked(colonnes).forEach { ligne ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ligne.forEach { CarteIndicateur(it, Modifier.weight(1f)) }
                    repeat(colonnes - ligne.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** Un nombre clé : la valeur en grand, son libellé dessous. */
@Composable
private fun CarteIndicateur(indicateur: Indicateur, modifier: Modifier) {
    val couleurs = LocalCouleurs.current
    val valeur = if (indicateur.enUtilisateurs) stringResource(R.string.admin_telemetrie_utilisateurs, indicateur.valeur) else indicateur.valeur
    val libelle = indicateur.detail?.let { stringResource(indicateur.libelle, it) } ?: stringResource(indicateur.libelle)
    Carte(modifier.semantics(mergeDescendants = true) {}) {
        Text(
            valeur,
            style = Typo.titre(MaterialTheme.typography.headlineSmall).copy(fontFeatureSettings = "tnum"),
            color = couleurs.text,
        )
        Text(libelle, style = MaterialTheme.typography.bodySmall, color = couleurs.textMuted)
    }
}

/**
 * « Pages les plus vues » : le chemin, ses vues et ses visiteurs uniques ; `accesParPage` :
 * la colonne « Accès » du site, quand la période a plusieurs canaux.
 */
@Composable
private fun Pages(pages: List<TableauTelemetrie.PageVue>, accesParPage: Boolean) {
    val couleurs = LocalCouleurs.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TitreSection(stringResource(R.string.admin_telemetrie_pages_vues))
        Carte {
            pages.forEachIndexed { rang, page ->
                if (rang > 0) HorizontalDivider(color = couleurs.border)
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp).semantics(mergeDescendants = true) {}) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            page.page,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            color = couleurs.text,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            stringResource(R.string.admin_telemetrie_vues_et_uniques, FormatsDuSite.nombre(page.total), FormatsDuSite.nombre(page.uniques)),
                            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                            color = couleurs.textMuted,
                        )
                    }
                    if (accesParPage) {
                        Text(LecturesTelemetrie.accesDe(page), style = MaterialTheme.typography.bodySmall, color = couleurs.textMuted)
                    }
                }
            }
        }
    }
}

/** « Utilisateurs actifs » : le nom servi, son statut, ses vues et pages, sa dernière connexion. */
@Composable
private fun Utilisateurs(utilisateurs: List<TableauTelemetrie.UtilisateurActif>) {
    val couleurs = LocalCouleurs.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TitreSection(stringResource(R.string.admin_telemetrie_utilisateurs_actifs))
        Carte {
            utilisateurs.forEachIndexed { rang, utilisateur ->
                if (rang > 0) HorizontalDivider(color = couleurs.border)
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp).semantics(mergeDescendants = true) {},
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(utilisateur.nom, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = couleurs.text)
                    val vues = stringResource(
                        R.string.admin_telemetrie_vues_et_pages,
                        FormatsDuSite.nombre(utilisateur.total),
                        FormatsDuSite.nombre(utilisateur.pages),
                    )
                    Text(
                        listOfNotNull(utilisateur.statut?.let { Roles.statut(it).libelle }, vues).joinToString(Separateur.point),
                        style = MaterialTheme.typography.bodySmall,
                        color = couleurs.textMuted,
                    )
                    utilisateur.derniereConnexion?.let {
                        Text(
                            stringResource(R.string.admin_telemetrie_derniere_connexion, FormatsDuSite.dateHeure(it)),
                            style = MaterialTheme.typography.bodySmall,
                            color = couleurs.textMuted,
                        )
                    }
                }
            }
        }
    }
}
