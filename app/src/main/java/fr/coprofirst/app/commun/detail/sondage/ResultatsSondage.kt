// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.detail.sondage

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.commun.Carte
import fr.coprofirst.app.commun.FlowCapsules
import fr.coprofirst.app.commun.PastilleTeinte
import fr.coprofirst.app.commun.animationsCoupees
import fr.coprofirst.app.commun.formats.FormatsDuSite

/**
 * Les résultats d'un sondage, comme l'iOS (`ResultatsSondage`, `ResultatsSondage.svelte` du
 * site) : une barre par réponse, son pourcentage et ses votes, « Mon vote ✓ », et sous chaque
 * option à préciser les réponses libres des votants. Masqués, ils ne sont pas des barres
 * vides : le serveur n'envoie pas les décomptes, et « 0 vote » se lirait « personne n'a voté ».
 */
@Composable
internal fun ResultatsSondage(sondage: DetailSondage) {
    val couleurs = LocalCouleurs.current
    Carte {
        if (sondage.resultatsVisibles) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                sondage.options.forEach { option ->
                    LigneResultat(option, sondage.totalVotes, mienne = option.id == sondage.monVote)
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.sondage_resultats_masques),
                    style = MaterialTheme.typography.bodyMedium,
                    color = couleurs.textMuted,
                    modifier = Modifier.testTag(ReperesSondage.RESULTATS_MASQUES),
                )
                sondage.options.firstOrNull { it.id == sondage.monVote }?.let { mienne ->
                    FlowCapsules(Modifier.semantics(mergeDescendants = true) {}) {
                        Text(
                            mienne.libelle,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                            color = couleurs.text,
                            modifier = Modifier.align(Alignment.CenterVertically),
                        )
                        PastilleTeinte(stringResource(R.string.sondage_mon_vote), "badge-blue", Modifier.align(Alignment.CenterVertically))
                    }
                }
            }
        }
    }
}

/**
 * Une réponse : libellé, ✏️, « Mon vote ✓ », la barre (qui se remplit une fois, à l'arrivée
 * des résultats ; d'un coup quand les animations de l'appareil sont coupées), le pourcentage et
 * les votes ; puis ses réponses libres, entre guillemets. Lue d'un bloc (repère
 * `sondage.resultat.<id>`).
 */
@Composable
private fun LigneResultat(option: OptionSondage, total: Int, mienne: Boolean) {
    val couleurs = LocalCouleurs.current
    val votes = option.nbVotes ?: 0
    val pourcentage = CalculsSondage.pourcentage(votes, total)
    val champ = stringResource(R.string.sondage_champ_precision)
    val coupees = animationsCoupees()
    val remplissage = remember { Animatable(if (coupees) pourcentage / 100f else 0f) }
    LaunchedEffect(pourcentage) {
        if (coupees) remplissage.snapTo(pourcentage / 100f) else remplissage.animateTo(pourcentage / 100f, tween(600))
    }
    Column(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.testTag(ReperesSondage.resultat(option.id)),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FlowCapsules {
            Text(
                option.libelle,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = couleurs.text,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
            if (option.champLibre) Text("✏️", Modifier.align(Alignment.CenterVertically).semantics { contentDescription = champ })
            if (mienne) PastilleTeinte(stringResource(R.string.sondage_mon_vote), "badge-blue", Modifier.align(Alignment.CenterVertically))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.weight(1f).height(8.dp).clip(CircleShape).background(couleurs.border.copy(alpha = 0.5f))
                    .clearAndSetSemantics {},
            ) {
                Box(
                    Modifier.fillMaxHeight().fillMaxWidth(remplissage.value).clip(CircleShape)
                        .background(if (mienne) couleurs.primary else couleurs.filViolet),
                )
            }
            Text(
                stringResource(R.string.sondage_pourcentage, FormatsDuSite.nombre(pourcentage)),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = couleurs.text,
            )
            Text(FormatsDuSite.nombre(votes), style = MaterialTheme.typography.bodyMedium, color = couleurs.textMuted)
        }
        if (option.champLibre && option.reponsesLibres.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                option.reponsesLibres.forEach { reponse ->
                    Row(Modifier.height(IntrinsicSize.Min).padding(start = 2.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        Box(Modifier.width(3.dp).fillMaxHeight().clip(CircleShape).background(couleurs.border))
                        Text(
                            "« $reponse »",
                            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                            color = couleurs.textMuted,
                        )
                    }
                }
            }
        }
    }
}
