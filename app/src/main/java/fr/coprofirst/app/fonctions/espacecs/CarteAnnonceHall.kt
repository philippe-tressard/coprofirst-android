// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.espacecs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.commun.Carte
import fr.coprofirst.app.commun.PastilleTeinte
import fr.coprofirst.app.commun.cliquable
import fr.coprofirst.app.commun.formats.FormatsDuSite
import fr.coprofirst.app.commun.texte.TexteRiche

/**
 * Une affiche (`HistoriqueAnnoncesHall.svelte`), comme l'iOS (`CarteAnnonceHall`) : QUAND,
 * puis son format, son titre et son aperçu ; dépliée, son message et ses envois. Ses gestes
 * passent par le menu « … » : renvoyer au CS, archiver ou restaurer, supprimer — ces deux
 * derniers seulement si le serveur les ouvre (`peut_*`).
 */
@Composable
internal fun CarteAnnonceHall(annonce: AnnonceHall, geste: (GesteAnnonceHall) -> Unit) {
    val c = LocalCouleurs.current
    var ouverte by rememberSaveable(annonce.id) { mutableStateOf(false) }
    val deplier = stringResource(R.string.espacecs_annonces_deplier)
    val replier = stringResource(R.string.espacecs_annonces_replier)
    Carte {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(FormatsDuSite.date(annonce.creeLe), style = MaterialTheme.typography.bodySmall, color = c.textMuted)
            PastilleTeinte(annonce.formatLabel, "badge-blue", compacte = true)
            Box(Modifier.weight(1f))
            MenuGestes(annonce, geste)
        }
        Column(
            Modifier.fillMaxWidth().cliquable(if (ouverte) replier else deplier) { ouverte = !ouverte }
                .testTag(ReperesEspaceCS.annonce(annonce.id)),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(annonce.titre, style = MaterialTheme.typography.titleMedium, color = c.text)
            if (!ouverte) {
                Text(annonce.apercu, style = MaterialTheme.typography.bodyMedium, color = c.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        AnimatedVisibility(ouverte) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TexteRiche(annonce.message)
                if (annonce.destinataires.isNotEmpty()) {
                    Text(
                        stringResource(
                            R.string.espacecs_annonces_envoyee,
                            FormatsDuSite.dateHeureCourte(annonce.envoyeLe ?: annonce.creeLe),
                            annonce.destinataires.joinToString(", "),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textMuted,
                    )
                }
            }
        }
        Marqueurs(annonce)
    }
}

/** La dernière ligne, dans l'ordre du site : état, lieu, provenance, envoi, auteur. */
@Composable
private fun Marqueurs(annonce: AnnonceHall) {
    val c = LocalCouleurs.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), itemVerticalAlignment = Alignment.CenterVertically) {
        if (annonce.archivee) PastilleTeinte(stringResource(R.string.espacecs_annonces_archivee), "badge-gray", compacte = true)
        annonce.lieu?.let { PastilleTeinte(it, "badge-gray", compacte = true) }
        if (annonce.ticketId != null) PastilleTeinte(stringResource(R.string.espacecs_annonces_actualite), "badge-gray", compacte = true)
        val n = annonce.destinataires.size
        if (n == 0) {
            PastilleTeinte(stringResource(R.string.espacecs_annonces_non_envoyee), "badge-orange", compacte = true)
        } else {
            PastilleTeinte(pluralStringResource(R.plurals.espacecs_annonces_destinataires, n, FormatsDuSite.nombre(n)), "badge-gray", compacte = true)
        }
        Text(annonce.auteurNom, style = MaterialTheme.typography.labelSmall, color = c.textMuted)
        if (annonce.assisteIa) {
            Icon(Icons.Outlined.AutoAwesome, contentDescription = stringResource(R.string.espacecs_annonces_assistant), tint = c.violet)
        }
    }
}

@Composable
private fun MenuGestes(annonce: AnnonceHall, geste: (GesteAnnonceHall) -> Unit) {
    var deplie by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { deplie = true }, modifier = Modifier.testTag(ReperesEspaceCS.annonceGestes(annonce.id))) {
            Icon(Icons.Outlined.MoreHoriz, contentDescription = stringResource(R.string.espacecs_annonces_gestes), tint = LocalCouleurs.current.primary)
        }
        DropdownMenu(expanded = deplie, onDismissRequest = { deplie = false }) {
            val choisir = { g: GesteAnnonceHall ->
                deplie = false
                geste(g)
            }
            Item(R.string.espacecs_annonces_renvoyer, Icons.Outlined.Email, ReperesEspaceCS.ANNONCE_RENVOYER) { choisir(GesteAnnonceHall.Renvoyer) }
            if (annonce.peutArchiver || annonce.peutRestaurer) {
                if (annonce.archivee) {
                    Item(R.string.espacecs_annonces_restaurer, Icons.AutoMirrored.Outlined.Undo, ReperesEspaceCS.ANNONCE_ARCHIVER) { choisir(GesteAnnonceHall.Archiver) }
                } else {
                    Item(R.string.espacecs_annonces_archiver, Icons.Outlined.Archive, ReperesEspaceCS.ANNONCE_ARCHIVER) { choisir(GesteAnnonceHall.Archiver) }
                }
            }
            if (annonce.peutSupprimer) {
                Item(R.string.espacecs_annonces_supprimer, Icons.Outlined.Delete, ReperesEspaceCS.ANNONCE_SUPPRIMER, danger = true) { choisir(GesteAnnonceHall.Supprimer) }
            }
        }
    }
}

@Composable
private fun Item(libelle: Int, icone: ImageVector, repere: String, danger: Boolean = false, onClick: () -> Unit) {
    val teinte = if (danger) LocalCouleurs.current.danger else LocalCouleurs.current.text
    DropdownMenuItem(
        text = { Text(stringResource(libelle), color = teinte) },
        leadingIcon = { Icon(icone, contentDescription = null, tint = teinte) },
        onClick = onClick,
        modifier = Modifier.testTag(repere),
    )
}
