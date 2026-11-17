// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.fil

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
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
 * fiche). Tourner la page : balayer la page de gauche, ou « Veille » / « Lendemain ». Les jours
 * sont ceux qui ont de l'activité : `GET /flux` ne sert aucune période.
 *
 * ⚠️ Pas de pincement pour la semaine (décision du 17/11/2026) : le fil d'une période sera servi
 * par l'API (coprofirst#2834) ; le geste n'apparaîtra qu'avec la version de contrat qui la sert —
 * aucun geste inerte d'ici là. La page ne « tourne » pas : les cartes du jour se fondent
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
 * L'éphéméride du jour ouvert : en tête la journée (« AUJOURD'HUI · MAR. 17 NOV. »), puis le
 * quantième en grand entre « Veille » et « Lendemain », puis le mois. Les flèches ont leur rangée :
 * sur une page étroite (ou en grand texte), la date ne s'écrase pas entre elles, et la hauteur de
 * l'éphéméride reste celle de trois lignes.
 */
@Composable
internal fun Ephemeride(jour: JourDuFil, maintenant: Instant, veille: (() -> Unit)?, lendemain: (() -> Unit)?) {
    val date = LocalDate.parse(jour.jour)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp).testTag(REPERE_EPHEMERIDE),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Libelle(TextesFil.entete(jour, maintenant), Modifier.padding(horizontal = 12.dp).semantics { heading() })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { veille?.invoke() }, enabled = veille != null, modifier = Modifier.testTag("fil.veille")) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.fil_veille))
            }
            Text(
                date.dayOfMonth.toString(),
                style = Typo.titre(MaterialTheme.typography.displayMedium),
                color = LocalCouleurs.current.text,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.weight(1f, fill = false),
            )
            IconButton(onClick = { lendemain?.invoke() }, enabled = lendemain != null, modifier = Modifier.testTag("fil.lendemain")) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.fil_lendemain))
            }
        }
        Text(
            FormatsDuSite.moisAnnee(jour.elements.first().date),
            style = MaterialTheme.typography.bodyMedium,
            color = LocalCouleurs.current.textMuted,
            maxLines = 1,
        )
    }
}

/**
 * Tourner la page d'un balayage horizontal : vers la gauche, le lendemain ; vers la droite, la
 * veille. Le retour par le bord reste au système (il prend le geste avant l'app). Relancé à
 * chaque page tournée (`jour`) : la veille et le lendemain sont ceux du jour affiché.
 */
internal fun Modifier.tournerLaPage(jour: String, veille: (() -> Unit)?, lendemain: (() -> Unit)?): Modifier = pointerInput(jour) {
    val seuil = SEUIL_DP.dp.toPx()
    var cumul = 0f
    detectHorizontalDragGestures(
        onDragStart = { cumul = 0f },
        onDragEnd = {
            when {
                abs(cumul) < seuil -> Unit
                cumul < 0 -> lendemain?.invoke()
                else -> veille?.invoke()
            }
        },
    ) { _, delta -> cumul += delta }
}

/** Le repère de l'éphéméride (tests d'interface). */
const val REPERE_EPHEMERIDE = "fil.ephemeride"

private const val SEUIL_DP = 64
