// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.commun.formats.FormatsDuSite
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

private val HAUTEUR_GRAPHE = 180.dp
private val MARGE_AXE = 40.dp

/** Le nombre d'étiquettes sous le graphe, comme l'iOS (`desiredCount: 6`). */
private const val ETIQUETTES = 6

/**
 * Le graphe d'une vue, comme l'iOS (`Chart` + `BarMark`) : un bâton par heure, jour,
 * mois ou année, des graduations rondes à droite, quelques étiquettes dessous. Chaque
 * bâton se lit en entier au lecteur d'écran (« 4 nov. : 37 vues, 12 uniques »).
 */
@Composable
fun GrapheBatons(batons: List<TableauTelemetrie.Baton>, portee: PorteeTelemetrie) {
    val couleurs = LocalCouleurs.current
    val ressources = LocalResources.current
    val graduations = Graduations.de(batons.maxOfOrNull { it.total } ?: 0)
    val haut = graduations.last().toFloat()
    Column {
        BoxWithConstraints(Modifier.fillMaxWidth().height(HAUTEUR_GRAPHE)) {
            val hauteur = maxHeight
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(end = MARGE_AXE)
                    .drawBehind {
                        graduations.forEach { g ->
                            val y = size.height * (1f - g / haut)
                            drawLine(couleurs.border, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                        }
                    },
            ) {
                batons.forEach { baton ->
                    val valeur = baton.uniques?.let {
                        ressources.getString(R.string.admin_telemetrie_vues_uniques, FormatsDuSite.nombre(baton.total), FormatsDuSite.nombre(it))
                    } ?: ressources.getString(R.string.admin_telemetrie_vues, FormatsDuSite.nombre(baton.total))
                    val dit = "${LecturesTelemetrie.libelle(baton.label, portee)} : $valeur"
                    Box(
                        Modifier.weight(1f).fillMaxHeight().semantics { contentDescription = dit },
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(0.7f)
                                .fillMaxHeight(baton.total / haut)
                                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                .background(couleurs.primary),
                        )
                    }
                }
            }
            graduations.forEach { g ->
                Text(
                    FormatsDuSite.nombre(g),
                    style = MaterialTheme.typography.labelSmall,
                    color = couleurs.textMuted,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(y = hauteur * (1f - g / haut) - 8.dp)
                        .clearAndSetSemantics {},
                )
            }
        }
        Etiquettes(batons, portee)
    }
}

/** Une étiquette tous les `pas` bâtons, sous le sien ; décoratives (chaque bâton se dit en entier). */
@Composable
private fun Etiquettes(batons: List<TableauTelemetrie.Baton>, portee: PorteeTelemetrie) {
    val pas = ceil(batons.size / ETIQUETTES.toDouble()).toInt().coerceAtLeast(1)
    Row(Modifier.fillMaxWidth().padding(end = MARGE_AXE, top = 4.dp).clearAndSetSemantics {}) {
        batons.forEachIndexed { rang, baton ->
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                if (rang % pas == 0) {
                    Text(
                        LecturesTelemetrie.etiquette(baton.label, portee),
                        style = MaterialTheme.typography.labelSmall,
                        color = LocalCouleurs.current.textMuted,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Visible,
                        modifier = Modifier.wrapContentWidth(unbounded = true),
                    )
                }
            }
        }
    }
}

/** Les graduations d'un axe : 0, puis un pas rond (1, 2 ou 5 × 10ⁿ) jusqu'au-delà du maximum. */
object Graduations {
    /** Environ trois intervalles, comme les axes automatiques. */
    private const val INTERVALLES = 3.0

    fun de(maximum: Int): List<Int> {
        if (maximum <= 0) return listOf(0, 1)
        val brut = maximum / INTERVALLES
        val ordre = 10.0.pow(floor(log10(brut)))
        val rond = when (brut / ordre) {
            in 0.0..1.0 -> 1.0
            in 1.0..2.0 -> 2.0
            in 2.0..5.0 -> 5.0
            else -> 10.0
        }
        val pas = (rond * ordre).toInt().coerceAtLeast(1)
        val nombre = ceil(maximum / pas.toDouble()).toInt()
        return (0..nombre).map { it * pas }
    }
}
