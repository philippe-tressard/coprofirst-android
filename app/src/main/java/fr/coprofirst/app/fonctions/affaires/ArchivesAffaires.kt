// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.affaires

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.charte.Typo
import fr.coprofirst.app.commun.EtatVide
import fr.coprofirst.app.commun.formats.FormatsDuSite
import kotlinx.coroutines.launch

/**
 * L'onglet Archives, comme l'iOS (`ArchivesAffaires`) : les affaires closes et les
 * actualités passées, sur trois ans, par année — chaque année se replie, la plus récente
 * s'ouvre d'elle-même (`ArchivesParAnnee` du site).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ArchivesAffaires(ctx: ContexteAffaires) {
    val etat = ctx.etat
    val portee = rememberCoroutineScope()
    var rafraichit by remember { mutableStateOf(false) }
    // Les années qu'on a dépliées ou repliées à la main ; les autres suivent la règle : la plus récente ouverte.
    var basculees by rememberSaveable { mutableStateOf(setOf<Int>()) }
    val annees = etat.archives

    PullToRefreshBox(
        isRefreshing = rafraichit,
        onRefresh = { portee.launch { rafraichit = true; etat.charger(); rafraichit = false } },
        modifier = Modifier.fillMaxSize(),
    ) {
        when (val phase = etat.phase) {
            EtatAffaires.Phase.Chargement -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is EtatAffaires.Phase.Erreur -> ErreurOnglet(R.string.archives_erreur, phase.erreur, Modifier.padding(Mise.marge)) {
                portee.launch { etat.charger() }
            }
            EtatAffaires.Phase.Pret -> if (annees.isEmpty()) {
                EtatVide(
                    stringResource(R.string.archives_aucune), Modifier.padding(Mise.marge), icone = Icons.Outlined.Archive,
                    explication = stringResource(R.string.archives_aucune_aide),
                )
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = Mise.marge, end = Mise.marge, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(Mise.ecart),
                ) {
                    annees.forEachIndexed { rang, groupe ->
                        val ouverte = (rang == 0) != (groupe.annee in basculees)
                        item(key = "annee-${groupe.annee}") {
                            EnTeteAnnee(groupe.annee, groupe.affaires.size, ouverte) {
                                basculees = if (groupe.annee in basculees) basculees - groupe.annee else basculees + groupe.annee
                            }
                        }
                        if (ouverte) {
                            items(groupe.affaires, key = { it.id }) { affaire ->
                                CarteAffaire(
                                    affaire, archive = true, ouvrir = { ctx.ouvrir(affaire) },
                                    modifier = Modifier.testTag(ReperesAffaires.archive(affaire.numero)),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** L'en-tête d'une année : l'année, son compte, le chevron — un toucher la replie ou la déplie. */
@Composable
private fun EnTeteAnnee(annee: Int, compte: Int, ouverte: Boolean, basculer: () -> Unit) {
    val c = LocalCouleurs.current
    val angle by animateFloatAsState(if (ouverte) 0f else -90f, label = "chevron")
    val vocal = pluralStringResource(R.plurals.archives_annee_vocal, compte, annee.toString(), compte)
    val etatDit = stringResource(if (ouverte) R.string.archives_depliee else R.string.archives_repliee)
    val aide = stringResource(if (ouverte) R.string.archives_replier else R.string.archives_deplier)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Mise.cible)
            .clickable(onClick = basculer)
            .clearAndSetSemantics {
                contentDescription = vocal
                stateDescription = etatDit
                heading()
                onClick(aide) { basculer(); true }
            }
            .testTag(ReperesAffaires.annee(annee)),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(annee.toString(), style = Typo.titre(MaterialTheme.typography.titleLarge), color = c.text)
        Text(
            FormatsDuSite.nombre(compte),
            Modifier.clip(CircleShape).background(c.neutreFond).padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.SemiBold, color = c.textMuted,
        )
        Box(Modifier.weight(1f))
        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = c.textMuted, modifier = Modifier.rotate(angle))
    }
}
