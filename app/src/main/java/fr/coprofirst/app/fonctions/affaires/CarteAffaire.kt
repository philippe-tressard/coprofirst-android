// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.affaires

import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.commun.Carte
import fr.coprofirst.app.commun.MessageErreur
import fr.coprofirst.app.commun.PastilleTeinte
import fr.coprofirst.app.commun.formats.FormatsDuSite
import fr.coprofirst.app.commun.texte.TexteDuSite
import fr.coprofirst.app.session.ErreurService

/**
 * Le statut d'une affaire en pastille (`BadgeEtatAffaire` du site), comme l'iOS
 * (`PastilleStatut`) : la teinte par son CODE (`TablesAffaires.pastillesStatut`, générée),
 * le libellé EN CLAIR que l'appelant tient du serveur.
 */
@Composable
fun PastilleStatut(statut: String, libelle: String, modifier: Modifier = Modifier, compacte: Boolean = false) {
    val vocal = stringResource(R.string.affaires_statut_vocal, libelle)
    PastilleTeinte(
        libelle, TablesAffaires.pastillesStatut[statut] ?: "badge-gray",
        modifier.semantics { contentDescription = vocal }, compacte = compacte,
    )
}

/**
 * Une carte d'affaire touchable : un toucher l'ouvre, un appui long ouvre ses `gestes`.
 * Une affaire urgente porte un liseré rouge à gauche ; une archivée s'atténue (« allure
 * d'archive » du site), sans perdre le contraste de son texte.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CarteTouchable(
    urgente: Boolean,
    archive: Boolean,
    vocal: String,
    ouvrir: () -> Unit,
    modifier: Modifier = Modifier,
    rayon: Dp = Mise.rayon,
    gestes: (() -> Unit)? = null,
    contenu: @Composable () -> Unit,
) {
    val danger = LocalCouleurs.current.danger
    val libelleGestes = stringResource(R.string.affaires_gestes)
    Box(
        modifier
            .clip(RoundedCornerShape(rayon))
            .combinedClickable(onLongClickLabel = gestes?.let { libelleGestes }, onLongClick = gestes, onClick = ouvrir)
            .desaturee(archive)
            .drawWithCache {
                val largeur = 4.dp.toPx()
                onDrawWithContent {
                    drawContent()
                    if (urgente && !archive) drawRect(danger, size = Size(largeur, size.height))
                }
            }
            .semantics(mergeDescendants = true) { contentDescription = vocal },
    ) { contenu() }
}

/** La saturation d'une archive (0,25, comme l'iOS) : la teinte s'efface, pas le texte. */
private fun Modifier.desaturee(actif: Boolean): Modifier = if (!actif) this else drawWithCache {
    val peinture = Paint().apply { colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0.25f) }) }
    onDrawWithContent {
        drawIntoCanvas { toile ->
            toile.saveLayer(Rect(Offset.Zero, size), peinture)
            drawContent()
            toile.restore()
        }
    }
}

/**
 * Une affaire dans la liste ou aux Archives, comme l'iOS (`CarteAffaire`) : date, urgence,
 * numéro, titre, aperçu sur trois lignes, statut et lieu.
 *
 * ⚠️ Divergences déclarées : la date s'écrit en jour court (« mar. 17 nov. »,
 * `FormatsDuSite.jourCourt`) — l'iOS la dit relative (« il y a 3 jours »), un format que
 * `FormatsDuSite` n'a pas (#89) ; « Qui la lit » se tait jusqu'au composant commun (#70).
 */
@Composable
internal fun CarteAffaire(affaire: Affaire, archive: Boolean, ouvrir: () -> Unit, modifier: Modifier = Modifier, gestes: (() -> Unit)? = null) {
    val c = LocalCouleurs.current
    val date = FormatsDuSite.jourCourt(affaire.creeLe)
    val vocal = listOfNotNull(
        if (affaire.urgente) stringResource(R.string.affaires_urgente) else null,
        affaire.titre, affaire.statutLibelle,
        if (archive) stringResource(R.string.affaires_archivee_vocal) else null,
        date, affaire.lieu, TexteDuSite.apercu(affaire.apercu),
    ).joinToString(", ")
    CarteTouchable(affaire.urgente, archive, vocal, ouvrir, modifier, gestes = gestes) {
        Carte {
            Column(Modifier.padding(start = if (affaire.urgente && !archive) 4.dp else 0.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(date, style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                    if (affaire.urgente) {
                        Text(
                            stringResource(R.string.affaires_urgente), style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold, color = c.danger,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(affaire.numero, style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"), color = c.textMuted)
                }
                Text(affaire.titre, style = MaterialTheme.typography.titleMedium, color = c.text)
                Text(
                    TexteDuSite.apercu(affaire.apercu), style = MaterialTheme.typography.bodyMedium, color = c.textMuted,
                    maxLines = 3, overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    PastilleStatut(affaire.statut, affaire.statutLibelle)
                    affaire.lieu?.let { Lieu(it) }
                }
            }
        }
    }
}

/**
 * Le lieu servi : l'épingle et le libellé (`EtiquetteLieu` de l'iOS). PROVISOIRE : il cède
 * la place à l'étiquette commune demandée par #70 dès qu'elle est sur `main`.
 */
@Composable
internal fun Lieu(lieu: String, modifier: Modifier = Modifier) {
    val c = LocalCouleurs.current
    val vocal = stringResource(R.string.affaire_lieu, lieu)
    Row(modifier.clearAndSetSemantics { contentDescription = vocal }, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Place, contentDescription = null, tint = c.textMuted, modifier = Modifier.size(16.dp))
        Text(lieu, style = MaterialTheme.typography.bodySmall, color = c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Le titre d'un onglet qui n'a pas pu s'afficher, la phrase de l'erreur et « Réessayer ». */
@Composable
internal fun ErreurOnglet(@StringRes titre: Int, erreur: ErreurService, modifier: Modifier = Modifier, reessayer: () -> Unit) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(titre), style = MaterialTheme.typography.titleMedium, color = LocalCouleurs.current.text)
        MessageErreur(erreur, reessayer = reessayer)
    }
}
