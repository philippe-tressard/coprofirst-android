// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.session.Installation

/**
 * Un bandeau sous la barre d'une page : une icône et une phrase, et dessous son détail —
 * lu d'une seule annonce. Le hors ligne (`BandeauHorsLigne`) et l'installation suspendue
 * (`BandeauInstallationSuspendue`) l'emploient ; `fond` et `encre` : des jetons de la charte.
 */
@Composable
fun BandeauSousLaBarre(
    icone: ImageVector,
    texte: String,
    detail: String?,
    repere: String,
    modifier: Modifier = Modifier,
    fond: Color = MaterialTheme.colorScheme.surfaceContainer,
    encre: Color = LocalCouleurs.current.text,
    discret: Color = LocalCouleurs.current.textMuted,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(fond)
            .padding(horizontal = Mise.marge, vertical = 8.dp)
            .semantics(mergeDescendants = true) {}
            .testTag(repere),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icone, contentDescription = null, tint = encre, modifier = Modifier.size(16.dp))
            Text(
                texte,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = encre,
                textAlign = TextAlign.Center,
            )
        }
        if (detail != null) {
            Text(detail, style = MaterialTheme.typography.labelSmall, color = discret, textAlign = TextAlign.Center)
        }
    }
}

/**
 * L'installation est SUSPENDUE (Registre R6, coprofirst#1882) : consultation seule, toute écriture
 * est refusée par le serveur (403, sa phrase affichée telle quelle). Comme le `BandeauInstallation`
 * du site : dès la connexion et sur chaque page de la résidence, sans bouton pour le fermer.
 * Rien hors suspension — `Installation.suspendue`, lu dans GET /config.
 *
 * ⚠️ Divergence déclarée : le texte du bandeau n'est pas publié avec la release ; il est écrit dans
 * les ressources (`installation_suspendue*`) jusqu'à sa publication (coprofirst#2840).
 */
@Composable
fun BandeauInstallationSuspendue(installation: Installation?, modifier: Modifier = Modifier) {
    if (installation?.suspendue != true) return
    val couleurs = LocalCouleurs.current
    BandeauSousLaBarre(
        Icons.Filled.Lock,
        stringResource(R.string.installation_suspendue),
        stringResource(R.string.installation_suspendue_detail),
        REPERE_BANDEAU_SUSPENDUE,
        fond = couleurs.warningFond,
        encre = couleurs.warningTexte,
        discret = couleurs.warningTexte,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** Le repère du bandeau de l'installation suspendue (celui que l'iOS reprendra, coprofirst-ios#262). */
const val REPERE_BANDEAU_SUSPENDUE = "bandeau.installation"
