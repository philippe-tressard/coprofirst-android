// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.fil

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.commun.cliquable

/**
 * Le logement de l'utilisateur, en tête du fil, comme l'en-tête de l'accueil du site et
 * l'iOS (`PastilleLogement`, #181) : l'épingle et le libellé servi (`libelle_logement`) ; un
 * toucher ouvre « Mes lots & accès ». Sans libellé servi (sans lot, ou API qui ne le sert
 * pas) : rien.
 *
 * ⚠️ Divergence déclarée : l'iOS le pose DANS la barre, à gauche (et sous le titre aux tailles
 * d'accessibilité) ; la barre d'Android porte déjà le titre, la pastille de la résidence et la
 * cloche — il prend ici la première ligne de la page, toute sa largeur, comme la forme
 * accessible de l'iOS.
 */
@Composable
internal fun LogementDuFil(logement: String?, ouvrirMesLots: () -> Unit) {
    if (logement == null) return
    val couleurs = LocalCouleurs.current
    val annonce = stringResource(R.string.fil_logement, logement)
    Row(
        Modifier.fillMaxWidth().heightIn(min = Mise.cible)
            .cliquable(stringResource(R.string.fil_logement_ouvre), ouvrirMesLots)
            .semantics { contentDescription = annonce }
            .padding(horizontal = Mise.marge)
            .testTag(REPERE_LOGEMENT),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Filled.Place, contentDescription = null, tint = couleurs.primary, modifier = Modifier.size(18.dp))
        Text(
            logement,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = couleurs.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Le repère du logement : celui de l'iOS (`fil.lot`). */
internal const val REPERE_LOGEMENT = "fil.lot"

/** La page « Mes lots & accès » (`mon-lot`), que le logement ouvre par la porte. */
internal const val PAGE_MES_LOTS = "mon-lot"
