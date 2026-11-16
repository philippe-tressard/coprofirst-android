// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.espacecs

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.charte.Couleurs
import fr.coprofirst.app.commun.Carte
import fr.coprofirst.app.commun.GRANDE_ECHELLE
import fr.coprofirst.app.commun.Libelle
import fr.coprofirst.app.commun.formats.FormatsDuSite

/**
 * Une RÉPARTITION en barres découpées selon l'autre axe (`BarresCroisees.svelte`), comme
 * l'iOS (`RepartitionCroisee`) : la longueur d'une barre est son nombre, rapporté à la plus
 * grande ; ses tranches la remplissent, chacune à sa part, de la teinte que le serveur leur
 * choisit. Le nombre s'écrit en bout de barre, la légende nomme chaque tranche : la couleur
 * ne porte jamais l'information seule, et le lecteur d'écran lit « Bât. A : 3, … ».
 */
@Composable
internal fun RepartitionCroisee(titre: String, barres: List<Repartition>) {
    val c = LocalCouleurs.current
    var deployee by remember { mutableStateOf(false) }
    val part by animateFloatAsState(if (deployee) 1f else 0f, tween(500, delayMillis = 100), label = "barres")
    LaunchedEffect(Unit) { deployee = true }
    val plusGrand = maxOf(1, barres.maxOfOrNull { it.nombre } ?: 1)
    val grand = LocalDensity.current.fontScale >= GRANDE_ECHELLE
    Carte {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Libelle(titre)
            barres.forEach { barre ->
                val lu = "${barre.libelle} : ${FormatsDuSite.nombre(barre.nombre)}" +
                    barre.tranches.joinToString("") { ", ${it.libelle} ${FormatsDuSite.nombre(it.nombre)}" }
                val piste = @Composable { m: Modifier -> Piste(barre, barre.nombre.toFloat() / plusGrand * part, m) }
                Column(Modifier.clearAndSetSemantics { contentDescription = lu }) {
                    if (grand) {
                        Text(barre.libelle, style = MaterialTheme.typography.bodyMedium, color = c.text)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            piste(Modifier.weight(1f))
                            Nombre(barre.nombre)
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(barre.libelle, style = MaterialTheme.typography.bodyMedium, color = c.text, maxLines = 2, modifier = Modifier.width(118.dp))
                            piste(Modifier.weight(1f))
                            Nombre(barre.nombre)
                        }
                    }
                }
            }
            Legende(barres.flatMap { it.tranches }.distinctBy { it.cle })
        }
    }
}

@Composable
private fun Nombre(n: Int) {
    Text(FormatsDuSite.nombre(n), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = LocalCouleurs.current.text)
}

/** La piste, et la part remplie : les tranches, séparées d'un filet de 2 dp. */
@Composable
private fun Piste(barre: Repartition, remplie: Float, modifier: Modifier) {
    val c = LocalCouleurs.current
    val forme = RoundedCornerShape(4.dp)
    Box(modifier.height(14.dp).clip(forme).background(c.neutreFond)) {
        Row(Modifier.fillMaxHeight().fillMaxWidth(remplie.coerceIn(0f, 1f)).clip(forme), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            barre.tranches.filter { it.nombre > 0 }.forEach { tranche ->
                TeinteDeTranche(tranche.teinte, Modifier.fillMaxHeight().weight(tranche.nombre.toFloat()))
            }
        }
    }
}

/** La légende : une pastille et un nom par tranche, qui passent à la ligne ; tue au lecteur d'écran (la barre se lit déjà). */
@Composable
private fun Legende(tranches: List<Repartition.Tranche>) {
    FlowRow(Modifier.clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        tranches.forEach { tranche ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TeinteDeTranche(tranche.teinte, Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)))
                Text(tranche.libelle, style = MaterialTheme.typography.labelSmall, color = LocalCouleurs.current.textMuted)
            }
        }
    }
}

/** Les couleurs d'une tranche, depuis les jetons de la charte (`TablesEspaceCS.tranches`) ; inconnue, `textMuted`. */
internal fun couleursDeTranche(teinte: String?, c: Couleurs): List<Color> =
    (teinte?.let { TablesEspaceCS.tranches[it] } ?: listOf { x: Couleurs -> x.textMuted }).map { it(c) }

/** Une tranche peinte : un aplat, ou des hachures quand la table en donne deux teintes. */
@Composable
private fun TeinteDeTranche(teinte: String?, modifier: Modifier) {
    val couleurs = couleursDeTranche(teinte, LocalCouleurs.current)
    Canvas(modifier) {
        drawRect(couleurs.last())
        if (couleurs.size > 1) {
            var x = -size.height
            while (x < size.width) {
                drawLine(couleurs.first(), Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = 2.dp.toPx())
                x += 5.dp.toPx()
            }
        }
    }
}
