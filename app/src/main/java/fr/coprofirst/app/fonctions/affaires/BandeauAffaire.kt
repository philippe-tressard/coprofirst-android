// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.affaires

import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.commun.AvisDuGeste
import fr.coprofirst.app.commun.TonAvis
import fr.coprofirst.app.commun.phrase
import fr.coprofirst.app.session.ErreurService
import kotlinx.coroutines.launch

/** Une feuille ouverte depuis le bandeau. */
private enum class FeuilleGeste { Repondre, Suite }

/** Le repère d'une icône du bandeau, celui de l'iOS (`affaire.<geste>`). */
fun repereGeste(geste: GesteAffaire) = "affaire.${geste.name.replaceFirstChar { it.lowercase() }}"

/**
 * Le bandeau d'une affaire, comme l'iOS (`BandeauAffaire`) : la rangée d'icônes du site
 * (`ActionsTicket`), sous le pouce, en bas de la fiche — une icône par geste que le serveur
 * ouvre à CE lecteur (`GesteAffaire.offerts`). Un geste réussi se dit dans l'avis, puis
 * l'affaire se relit.
 *
 * ⚠️ « Modifier » l'affaire et les corrections de sa suite arrivent avec la PR suivante de #16 ;
 * les options et « Archiver » (`PATCH /tickets/{id}`) attendent #99 (`GesteAffaire.offerts`).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BandeauAffaire(
    affaire: DetailAffaire,
    conseil: Boolean,
    service: ServiceAffaires,
    lien: String?,
    avis: AvisDuGeste,
    recharger: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalCouleurs.current
    val ressources = LocalResources.current
    val presse = LocalClipboard.current
    val contexte = LocalContext.current
    val portee = rememberCoroutineScope()
    var feuille by remember { mutableStateOf<FeuilleGeste?>(null) }
    var enCours by remember { mutableStateOf(false) }
    var partage by remember { mutableStateOf(false) }
    var options by remember { mutableStateOf(false) }
    var echec by remember { mutableStateOf<ErreurService?>(null) }
    val phraseEchec = echec?.phrase()
    LaunchedEffect(phraseEchec) {
        if (phraseEchec != null) {
            avis.dire(phraseEchec, TonAvis.Erreur)
            echec = null
        }
    }
    val gestes = GesteAffaire.offerts(affaire, conseil).filter { it != GesteAffaire.Lien || lien != null }

    /** Un geste sans feuille : envoyé, annoncé, puis l'affaire relue. */
    fun geste(phrase: String, envoi: suspend () -> Unit) {
        if (enCours) return
        portee.launch {
            enCours = true
            try {
                envoi()
                avis.dire(phrase)
                recharger()
            } catch (e: ErreurService) {
                echec = e
            } finally {
                enCours = false
            }
        }
    }

    Row(
        modifier.fillMaxWidth().background(c.surface).navigationBarsPadding().padding(horizontal = Mise.marge, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        gestes.forEach { g ->
            val libelle = stringResource(libelleGeste(g))
            when (g) {
                // Un toucher COPIE, comme le site (`BoutonLien`) ; l'appui long offre le partage.
                GesteAffaire.Lien -> Box {
                    Box(
                        Modifier.size(Mise.cible).clip(CircleShape)
                            .combinedClickable(
                                role = Role.Button,
                                onLongClickLabel = stringResource(R.string.geste_partager),
                                onLongClick = { partage = true },
                            ) {
                                portee.launch { presse.setClipEntry(ClipEntry(ClipData.newPlainText(affaire.numero, lien))) }
                                avis.dire(ressources.getString(R.string.geste_lien_copie))
                            }
                            .semantics { contentDescription = libelle }
                            .testTag(repereGeste(g)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(icone(g), contentDescription = null, tint = c.primary) }
                    DropdownMenu(partage, { partage = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.geste_partager)) }, onClick = {
                            partage = false
                            val envoi = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, lien)
                            contexte.startActivity(Intent.createChooser(envoi, null))
                        })
                    }
                }
                GesteAffaire.Options -> Box {
                    IconButton({ options = true }, Modifier.testTag(repereGeste(g)), enabled = !enCours) {
                        Icon(icone(g), contentDescription = libelle, tint = c.primary)
                    }
                    DropdownMenu(options, { options = false }) {
                        TablesAffaires.options.forEach { option ->
                            val active = option.active(affaire)
                            DropdownMenuItem(
                                text = { Text(option.etat) },
                                leadingIcon = { Checkbox(active, onCheckedChange = null) },
                                onClick = {
                                    options = false
                                    val phrase = if (active) ressources.getString(R.string.geste_option_retiree, option.etat) else option.etat
                                    geste(phrase) { service.regler(affaire.id, option, !active) }
                                },
                                modifier = Modifier.testTag("affaire.option.${option.cle}"),
                            )
                        }
                    }
                }
                GesteAffaire.Archiver -> IconButton(
                    { geste(ressources.getString(R.string.geste_archivee)) { service.archiver(affaire.id) } },
                    Modifier.testTag(repereGeste(g)), enabled = !enCours,
                ) { Icon(icone(g), contentDescription = libelle, tint = c.primary) }
                GesteAffaire.Repondre, GesteAffaire.Suite -> IconButton(
                    { feuille = if (g == GesteAffaire.Repondre) FeuilleGeste.Repondre else FeuilleGeste.Suite },
                    Modifier.testTag(repereGeste(g)),
                ) { Icon(icone(g), contentDescription = libelle, tint = c.primary) }
            }
        }
    }
    HorizontalDivider(color = c.border)

    // La phrase attend que la feuille soit fermée : sinon elle passerait derrière.
    val fermer: () -> Unit = {
        feuille = null
        avis.feuilleFermee()
    }
    when (feuille) {
        FeuilleGeste.Repondre -> FeuilleRepondre(affaire, service, fermer) {
            avis.direApresFermeture(ressources.getString(R.string.geste_reponse_envoyee))
            recharger()
        }
        FeuilleGeste.Suite -> FeuilleSuite(affaire, service, fermer) { changeEtat ->
            avis.direApresFermeture(ressources.getString(if (changeEtat) R.string.geste_statut_mis_a_jour else R.string.geste_commentaire_ajoute))
            recharger()
        }
        null -> Unit
    }
}

private fun libelleGeste(g: GesteAffaire) = when (g) {
    GesteAffaire.Lien -> R.string.geste_lien
    GesteAffaire.Repondre -> R.string.geste_repondre
    GesteAffaire.Suite -> R.string.geste_suite
    GesteAffaire.Options -> R.string.geste_options
    GesteAffaire.Archiver -> R.string.geste_archiver
}

/** L'icône de Material la plus proche de celle du site (`IconeAction`, Lucide). */
private fun icone(g: GesteAffaire): ImageVector = when (g) {
    GesteAffaire.Lien -> Icons.Outlined.Link
    GesteAffaire.Repondre -> Icons.AutoMirrored.Outlined.Chat
    GesteAffaire.Suite -> Icons.Outlined.Sync
    GesteAffaire.Options -> Icons.Outlined.MoreHoriz
    GesteAffaire.Archiver -> Icons.Outlined.Archive
}
