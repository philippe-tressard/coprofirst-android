// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.fil

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.charte.Typo
import fr.coprofirst.app.commun.Libelle
import fr.coprofirst.app.commun.fil.JourDuFil
import fr.coprofirst.app.commun.fil.TextesFil
import fr.coprofirst.app.commun.formats.FormatsDuSite
import java.time.Instant
import java.time.LocalDate
import kotlin.math.abs

/**
 * Le Fil d'un CARNET (#242), comme l'iOS (coprofirst-ios#119) : une journée par double page — à
 * gauche, la date en éphéméride et les cartes du jour ; à droite, la carte choisie (la colonne de
 * fiche). Tourner la page : balayer la page de gauche, ou « Veille » / « Lendemain » au pied de
 * la page. Les jours sont ceux qui ont de l'activité dans le fil.
 *
 * PINCER la page ouvre sa semaine (#286, `PageDeLaSemaine.kt`), servie par l'API
 * (`GET /flux?du=&au=`). La page ne « tourne » pas : les cartes du jour se fondent
 * (`animateItem`), le mouvement réduit les pose sans transition.
 */
object FeuilletsDuFil {
    /** Le jour affiché : celui demandé s'il a encore des cartes, sinon le plus récent. */
    fun jour(jours: List<JourDuFil>, demande: String?): JourDuFil? = jours.firstOrNull { it.jour == demande } ?: jours.firstOrNull()

    /** La veille : le jour d'activité d'avant (les jours vont du plus récent au plus ancien). */
    fun veille(jours: List<JourDuFil>, jour: String): String? = voisin(jours, jour, +1)

    /** Le lendemain : le jour d'activité d'après ; aucun après le plus récent. */
    fun lendemain(jours: List<JourDuFil>, jour: String): String? = voisin(jours, jour, -1)

    private fun voisin(jours: List<JourDuFil>, jour: String, pas: Int): String? =
        jours.indexOfFirst { it.jour == jour }.takeIf { it >= 0 }?.let { jours.getOrNull(it + pas)?.jour }
}

/**
 * L'éphéméride du jour ouvert, comme l'iOS (`Ephemeride`, coprofirst-ios#275) : la ligne d'en-tête
 * du fil (« AUJOURD'HUI · MAR. 17 NOV. »), le quantième en grand, le mois et l'année dessous,
 * alignés à gauche. Le lecteur d'écran n'en lit qu'une chose : la date longue, en titre.
 */
@Composable
internal fun Ephemeride(jour: JourDuFil, maintenant: Instant, voirLaSemaine: (() -> Unit)? = null) {
    val premier = jour.elements.first().date
    val couleurs = LocalCouleurs.current
    val semaine = stringResource(R.string.carnet_voir_la_semaine)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Mise.marge, vertical = 16.dp)
            .testTag(REPERE_EPHEMERIDE)
            .clearAndSetSemantics {
                contentDescription = FormatsDuSite.dateLongue(premier)
                heading()
                // Le pincement, au lecteur d'écran (« Voir la semaine » de l'iOS, #286).
                if (voirLaSemaine != null) customActions = listOf(CustomAccessibilityAction(semaine) { voirLaSemaine(); true })
            },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Libelle(TextesFil.entete(jour, maintenant))
        Text(
            LocalDate.parse(jour.jour).dayOfMonth.toString(),
            style = Typo.titre(MaterialTheme.typography.displayMedium),
            color = couleurs.text,
            maxLines = 1,
        )
        Text(FormatsDuSite.moisAnnee(premier), style = MaterialTheme.typography.bodyMedium, color = couleurs.textMuted, maxLines = 1)
    }
}

/**
 * Le pied de la page du jour, comme l'iOS : « ‹ Veille » à gauche, « Lendemain › » à droite, comme
 * les pages d'un carnet. Un bouton sans jour où aller est inactif : pas de lendemain après aujourd'hui.
 */
@Composable
internal fun PiedDuJour(veille: (() -> Unit)?, lendemain: (() -> Unit)?) = PiedDeFeuillet(
    stringResource(R.string.fil_veille), veille, "carnet.veille",
    stringResource(R.string.fil_lendemain), lendemain, "carnet.lendemain",
)

/**
 * Le pied d'un feuillet du carnet — le jour, la semaine (#286, `FeuilletDuCarnet` de l'iOS) : le
 * bouton d'avant à gauche, celui d'après à droite. Quand les deux mots ne tiennent pas (très grand
 * texte), les chevrons seuls, leur nom gardé pour le lecteur d'écran. Sans page où aller : inactif.
 */
@Composable
internal fun PiedDeFeuillet(
    titreAvant: String,
    avant: (() -> Unit)?,
    repereAvant: String,
    titreApres: String,
    apres: (() -> Unit)?,
    repereApres: String,
) {
    val couleurs = LocalCouleurs.current
    val style = MaterialTheme.typography.labelLarge
    val mesure = rememberTextMeasurer()
    Column(Modifier.fillMaxWidth().background(couleurs.surface)) {
        HorizontalDivider(color = couleurs.border)
        BoxWithConstraints(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = Mise.marge)) {
            // Ce que demandent les deux boutons titrés : leurs mots, leurs chevrons, leurs marges.
            val titres = mesure.measure(titreAvant, style).size.width + mesure.measure(titreApres, style).size.width
            val avecTitres = with(LocalDensity.current) { titres.toDp() } + PAR_BOUTON * 2 <= maxWidth
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BoutonDePage(titreAvant, Icons.AutoMirrored.Filled.KeyboardArrowLeft, avant = true, avecTitres, avant, repereAvant)
                Spacer(Modifier.weight(1f))
                BoutonDePage(titreApres, Icons.AutoMirrored.Filled.KeyboardArrowRight, avant = false, avecTitres, apres, repereApres)
            }
        }
    }
}

/** Un bouton du pied de page : le chevron du côté où va la page, le titre s'il tient. */
@Composable
private fun BoutonDePage(titre: String, chevron: ImageVector, avant: Boolean, avecTitre: Boolean, tourner: (() -> Unit)?, repere: String) {
    TextButton(
        onClick = { tourner?.invoke() },
        enabled = tourner != null,
        modifier = Modifier.heightIn(min = 48.dp).widthIn(min = 48.dp).testTag(repere).semantics { contentDescription = titre },
        contentPadding = PaddingValues(horizontal = 8.dp),
    ) {
        if (avant) Icon(chevron, contentDescription = null)
        if (avecTitre) Text(titre, maxLines = 1)
        if (!avant) Icon(chevron, contentDescription = null)
    }
}

/** Un bouton titré, hors de son mot : son chevron (24), l'écart, ses marges (2 × 8). */
private val PAR_BOUTON = 48.dp

/**
 * Tourner la page d'un balayage horizontal : vers la gauche, la suivante (le lendemain, la semaine
 * d'après) ; vers la droite, la précédente. Un balayage qui part d'un bord (à moins d'une marge)
 * n'est pas le sien : les bords restent au geste « retour » du système, comme l'iOS laisse le bord
 * gauche au sien. Relancé à chaque page tournée (`page`) : avant et après sont ceux de la page affichée.
 */
internal fun Modifier.tournerLaPage(page: String, veille: (() -> Unit)?, lendemain: (() -> Unit)?): Modifier = pointerInput(page) {
    val seuil = SEUIL_DP.dp.toPx()
    val bord = Mise.marge.toPx()
    var cumul = 0f
    var loinDuBord = false
    detectHorizontalDragGestures(
        onDragStart = { depart ->
            cumul = 0f
            loinDuBord = depart.x > bord && depart.x < size.width - bord
        },
        onDragEnd = {
            when {
                !loinDuBord || abs(cumul) < seuil -> Unit
                cumul < 0 -> lendemain?.invoke()
                else -> veille?.invoke()
            }
        },
    ) { _, delta -> cumul += delta }
}

/** Le repère de l'éphéméride (tests d'interface). */
const val REPERE_EPHEMERIDE = "fil.ephemeride"

private const val SEUIL_DP = 64
