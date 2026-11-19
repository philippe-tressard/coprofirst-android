// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.fil

import fr.coprofirst.app.commun.GRANDE_ECHELLE
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.charte.Pictogrammes
import fr.coprofirst.app.charte.TracesDuSite
import fr.coprofirst.app.commun.Carte
import fr.coprofirst.app.commun.PastilleTeinte
import fr.coprofirst.app.commun.formats.FormatsDuSite
import fr.coprofirst.app.commun.lecture.EtiquetteLieu
import fr.coprofirst.app.commun.lecture.Lecture
import fr.coprofirst.app.commun.lecture.PastilleLecture
import fr.coprofirst.app.commun.pieces.VignettePieces
import fr.coprofirst.app.commun.texte.TexteDuSite
import fr.coprofirst.app.session.SessionOuverte
import java.time.Instant

/** Le repère d'une carte du fil, celui de l'iOS : `fil.<type>:<id>`. */
fun repereCarte(element: ElementDuFil) = "fil.${element.id}"

/**
 * Un élément du fil, comme l'iOS (`CarteDuFil`) : sa gouttière (`VignetteDeNature`), la
 * rubrique, l'heure et la nature ; l'échéance d'une affaire datée sous ce classement
 * (`VignetteEcheance`) ; le titre, une ou deux lignes de texte ; qui la lit et son lieu. Ses
 * pièces jointes, une vignette à droite (`VignettePieces`) — lue par la session, absente sans
 * elle (le squelette). Une urgence porte le liseré rouge à gauche. `ouvrir` : le toucher et le
 * chevron ; `null`, la carte ne s'ouvre pas (`DestinationDuFil`).
 */
@Composable
fun CarteDuFil(
    element: ElementDuFil,
    session: SessionOuverte?,
    modifier: Modifier = Modifier,
    maintenant: Instant = Instant.now(),
    action: String? = null,
    ouvrir: (() -> Unit)? = null,
) {
    val couleurs = LocalCouleurs.current
    val vocal = TextesFil.carte(element)
    val rouge = couleurs.danger
    val urgent = element.urgent
    Carte(
        modifier
            .testTag(repereCarte(element))
            .semantics(mergeDescendants = true) {}
            .clip(RoundedCornerShape(Mise.rayon))
            .drawWithContent {
                drawContent()
                if (urgent) drawRect(rouge, size = Size(LISERE.toPx(), size.height))
            },
        ouvrir = ouvrir,
        action = action,
    ) {
        val disposition = Modifier.fillMaxWidth().padding(start = if (urgent) LISERE else 0.dp).clearAndSetSemantics { contentDescription = vocal }
        // Au très grand texte, la carte s'EMPILE (#295, comme l'iOS #266) : la gouttière, les
        // pièces et le chevron sur une rangée, puis le texte sur toute la largeur. En ligne,
        // ils lui laissaient une colonne où « Urgent » se coupait en syllabes.
        if (empilee(LocalDensity.current.fontScale)) {
            Column(disposition, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ESPACE)) {
                    VignetteDeNature(element)
                    Spacer(Modifier.weight(1f))
                    Ornements(element, session, ouvrir != null)
                }
                TexteDeCarte(element, maintenant, Modifier.fillMaxWidth())
            }
        } else {
            Row(disposition, horizontalArrangement = Arrangement.spacedBy(ESPACE)) {
                VignetteDeNature(element)
                TexteDeCarte(element, maintenant, Modifier.weight(1f))
                // Hors grand texte, rien ne change : les pièces en haut, le chevron au milieu.
                Ornements(element, session, ouvrir != null, Modifier.align(Alignment.CenterVertically))
            }
        }
    }
}

/** La carte s'empile-t-elle à cette échelle du texte ? Aux tailles d'accessibilité (`GRANDE_ECHELLE`). */
internal fun empilee(echelle: Float): Boolean = echelle >= GRANDE_ECHELLE

/** Rubrique et heure, l'échéance, le titre, l'aperçu, qui la lit et où. */
@Composable
private fun TexteDeCarte(element: ElementDuFil, maintenant: Instant, modifier: Modifier) {
    val couleurs = LocalCouleurs.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        EnTeteCarte(element)
        element.echeance?.let { VignetteEcheance(it, element.nature.couleur(couleurs), maintenant) }
        Text(element.titre, style = MaterialTheme.typography.titleMedium, color = couleurs.text)
        val detail = element.detail?.let(TexteDuSite::apercu).orEmpty()
        if (detail.isNotEmpty()) {
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = couleurs.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        // Qui la lit, puis où (#129, #130) — rien hors affaire ni pour le défaut.
        if (Lecture.aMontrer(element.meta.lecture) != null || element.meta.lieu != null) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PastilleLecture(element.meta.lecture)
                EtiquetteLieu(element.meta.lieu)
            }
        }
    }
}

/**
 * Les pièces jointes (lues par la session, absentes sans elle) et le chevron d'une carte qui
 * s'ouvre, posés dans la rangée de l'appelant. `modifier` : la place du chevron dans la rangée.
 */
@Composable
private fun Ornements(element: ElementDuFil, session: SessionOuverte?, chevron: Boolean, modifier: Modifier = Modifier) {
    if (session != null) VignettePieces(element.pieces, session)
    if (chevron) {
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = LocalCouleurs.current.textMuted, modifier = modifier)
    }
}

/** Rubrique, heure, puis nature : sur une ligne tant qu'elle tient, sinon l'une sous l'autre — un libellé ne se coupe jamais. */
@Composable
private fun EnTeteCarte(element: ElementDuFil) {
    val couleurs = LocalCouleurs.current
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            TextesFil.rubrique(element),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = if (element.urgent) couleurs.danger else element.nature.couleur(couleurs),
        )
        Text(FormatsDuSite.heure(element.date), style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = CHIFFRES), color = couleurs.textMuted)
        element.natureEnPastille?.let { PastilleTeinte(it.libelle, it.badge, compacte = true) }
    }
}

/**
 * La gouttière d'une carte du fil, comme l'iOS (`VignetteDeNature`) : le pictogramme qui la
 * CLASSE (`classeDuFlux` du site), dans la teinte de sa rubrique. Décorative.
 */
@Composable
fun VignetteDeNature(element: ElementDuFil) {
    val couleurs = LocalCouleurs.current
    val nature = element.nature
    Box(
        Modifier.size(36.dp).background(nature.fond(couleurs), RoundedCornerShape(10.dp)).clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        // Le TRACÉ publié par le site (#183), comme l'iOS ; l'équivalent de Material s'il manque.
        val icone = TracesDuSite.icone(element.pictogramme) ?: Pictogrammes.icone(element.pictogramme)
        Icon(icone, contentDescription = null, tint = nature.couleur(couleurs), modifier = Modifier.size(21.dp))
    }
}

/**
 * La vignette d'échéance d'une carte (#75), comme l'iOS (`VignetteEcheance`) : le délai servi,
 * à la teinte de la nature (gris une fois passé), puis la ou les dates — sur une ligne, les
 * dates passent dessous au très grand texte. Rien d'animé : la vignette se lit.
 */
@Composable
fun VignetteEcheance(echeance: EcheanceDuFil, teinte: Color, maintenant: Instant = Instant.now()) {
    val couleurs = LocalCouleurs.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), itemVerticalAlignment = Alignment.Bottom) {
        echeance.libelle?.let {
            Text(
                it,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = CHIFFRES),
                color = if (echeance.passee) couleurs.textMuted else teinte,
            )
        }
        Text(echeance.dates(maintenant), style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = CHIFFRES), color = couleurs.textMuted)
    }
}

/** Le liseré d'une urgence. */
private val LISERE = 4.dp

/** L'écart entre la gouttière, le texte et les ornements. */
private val ESPACE = 12.dp

/** Des chiffres à chasse fixe (`monospacedDigit` de l'iOS) : l'heure et le délai ne dansent pas. */
private const val CHIFFRES = "tnum"
