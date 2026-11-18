// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.reponses

import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.coprofirst.api.modeles.ReponseCommunauteRead
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.commun.FlowCapsules
import fr.coprofirst.app.commun.PastilleTeinte
import fr.coprofirst.app.commun.formats.FormatsDuSite

/**
 * Une réponse, comme l'iOS (`LigneReponse`) : son auteur, son bâtiment et son rôle, ⭐ pour le
 * conseil syndical, la date, puis le texte (sélectionnable) — sur le fond bleu du site pour le
 * conseil syndical. Ses gestes (`supprimer`, `signaler` : `null` quand ils ne s'offrent pas)
 * sont sous « ⋮ » ; sans geste, pas de bouton.
 */
@Composable
internal fun LigneReponse(
    reponse: ReponseCommunauteRead,
    supprimer: (() -> Unit)?,
    signaler: (() -> Unit)?,
    /** « Masquer ce que publie… » son auteur (#282) ; `null` : le geste ne s'offre pas. */
    masquer: (() -> Unit)? = null,
) {
    val couleurs = LocalCouleurs.current
    val cs = Reponses.estCs(reponse)
    val forme = RoundedCornerShape(Mise.rayon)
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (cs) couleurs.infoFond else couleurs.surface, forme)
            .border(1.dp, if (cs) couleurs.infoBordure else couleurs.border, forme)
            .padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp)
            .testTag(ReperesReponses.reponse(reponse.id)),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f).padding(end = 6.dp).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FlowCapsules {
                Text(
                    Reponses.auteur(reponse) ?: stringResource(R.string.reponses_auteur_inconnu),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = couleurs.text,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
                reponse.auteurBatiment?.let { PastilleTeinte(it, "badge-gray", Modifier.align(Alignment.CenterVertically), compacte = true) }
                reponse.auteurRole?.let {
                    PastilleTeinte(it, if (cs) "badge-blue" else "badge-gray", Modifier.align(Alignment.CenterVertically), compacte = true)
                }
                if (cs) Text("⭐", Modifier.align(Alignment.CenterVertically).clearAndSetSemantics {})
                Text(
                    FormatsDuSite.dateCourte(reponse.creeLe.toInstant()),
                    style = MaterialTheme.typography.labelMedium,
                    color = couleurs.textMuted,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }
            SelectionContainer {
                Text(reponse.contenu, style = MaterialTheme.typography.bodyLarge, color = couleurs.text)
            }
        }
        if (supprimer != null || signaler != null || masquer != null) GestesDUneReponse(reponse, supprimer, signaler, masquer)
    }
}

/** « ⋮ », puis « Signaler cette réponse », « Masquer ce que publie… » et « Supprimer » (en rouge), selon ce qui s'offre. */
@Composable
private fun GestesDUneReponse(reponse: ReponseCommunauteRead, supprimer: (() -> Unit)?, signaler: (() -> Unit)?, masquer: (() -> Unit)?) {
    val id = reponse.id
    val couleurs = LocalCouleurs.current
    var ouvert by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { ouvert = true }, modifier = Modifier.testTag(ReperesReponses.actions(id))) {
            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.reponses_actions), tint = couleurs.textMuted)
        }
        DropdownMenu(expanded = ouvert, onDismissRequest = { ouvert = false }) {
            if (signaler != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.reponses_signaler)) },
                    leadingIcon = { Icon(Icons.Outlined.Flag, contentDescription = null, tint = couleurs.orange) },
                    onClick = {
                        ouvert = false
                        signaler()
                    },
                    modifier = Modifier.testTag(ReperesReponses.signaler(id)),
                )
            }
            if (masquer != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.masquage_geste, Reponses.auteur(reponse).orEmpty())) },
                    leadingIcon = { Icon(Icons.Outlined.VisibilityOff, contentDescription = null, tint = couleurs.textMuted) },
                    onClick = {
                        ouvert = false
                        masquer()
                    },
                    modifier = Modifier.testTag(ReperesReponses.masquer(id)),
                )
            }
            if (supprimer != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.reponses_supprimer), color = couleurs.danger) },
                    leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = couleurs.danger) },
                    onClick = {
                        ouvert = false
                        supprimer()
                    },
                    modifier = Modifier.testTag(ReperesReponses.supprimer(id)),
                )
            }
        }
    }
}
