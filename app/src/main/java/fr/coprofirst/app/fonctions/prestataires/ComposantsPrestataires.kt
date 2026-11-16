// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.prestataires

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.commun.Libelle
import fr.coprofirst.app.commun.Pastille
import fr.coprofirst.app.commun.cliquable
import fr.coprofirst.app.commun.formats.FormatsDuSite

/**
 * L'en-tête d'une carte dépliable (prestataire, contrat) : le titre et un chevron qui
 * tourne ; tout l'en-tête déplie ou replie, et le dit au lecteur d'écran.
 */
@Composable
internal fun EnteteDepliable(titre: String, deplie: Boolean, repere: String, basculer: () -> Unit) {
    val couleurs = LocalCouleurs.current
    val angle by animateFloatAsState(if (deplie) DEMI_TOUR else 0f, tween(DUREE_MS), label = "chevron")
    val action = stringResource(if (deplie) R.string.prestataires_replier_fiche else R.string.prestataires_deplier_fiche)
    Row(
        Modifier.fillMaxWidth().heightIn(min = Mise.cible).testTag(repere).cliquable(action, basculer),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            titre,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = couleurs.text,
            modifier = Modifier.weight(1f),
        )
        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = couleurs.textMuted, modifier = Modifier.rotate(angle))
    }
}

/** Le détail d'une carte, qui paraît en fondu quand elle se déplie. */
@Composable
internal fun Depliage(visible: Boolean, contenu: @Composable () -> Unit) {
    AnimatedVisibility(visible, enter = fadeIn(tween(DUREE_MS)), exit = fadeOut(tween(DUREE_MS))) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { contenu() }
    }
}

/** Les archives repliées en bas d'une liste (`DisclosureGroup` de l'iOS) : « 📁 Archives (N) ». */
@Composable
internal fun GroupeArchives(nombre: Int, contenu: @Composable () -> Unit) {
    if (nombre == 0) return
    var ouvert by rememberSaveable { mutableStateOf(false) }
    val couleurs = LocalCouleurs.current
    val angle by animateFloatAsState(if (ouvert) 0f else -QUART_DE_TOUR, tween(DUREE_MS), label = "archives")
    val etat = stringResource(if (ouvert) R.string.prestataires_replier else R.string.prestataires_deplier)
    Column(verticalArrangement = Arrangement.spacedBy(Mise.ecart)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = Mise.cible).testTag(ReperesPrestataires.ARCHIVES)
                .semantics { stateDescription = etat }
                .cliquable(etat) { ouvert = !ouvert },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.prestataires_archives, FormatsDuSite.nombre(nombre)),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = couleurs.text,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = couleurs.primary, modifier = Modifier.rotate(angle))
        }
        Depliage(ouvert, contenu)
    }
}

/** Un filtre en pastilles qui défilent de côté : « Tous », puis chaque option ; retoucher une option la retire. */
@Composable
internal fun FiltrePastilles(titre: String, options: List<Pair<String, String>>, valeur: String?, choisir: (String?) -> Unit) {
    val haptique = LocalHapticFeedback.current
    val changer = { v: String? ->
        haptique.performHapticFeedback(HapticFeedbackType.SegmentTick)
        choisir(v)
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Libelle(titre, Modifier.semantics { heading() })
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pastille(stringResource(R.string.prestataires_tous), retenue = valeur == null) { changer(null) }
            options.forEach { (code, libelle) ->
                // Sans le compte du site, comme l'iOS.
                Pastille(libelle, retenue = valeur == code) { changer(if (valeur == code) null else code) }
            }
        }
    }
}

private const val DUREE_MS = 220
private const val DEMI_TOUR = 180f
private const val QUART_DE_TOUR = 90f
