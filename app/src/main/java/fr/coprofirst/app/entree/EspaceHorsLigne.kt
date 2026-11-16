// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.entree

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.commun.formats.FormatsDuSite
import java.time.Instant

/**
 * L'espace de la résidence, hors ligne (#28) : le bandeau en haut tant que ça dure — jamais
 * une alerte —, et la reprise de la session dès que le réseau revient ou que l'app revient
 * au premier plan pendant une consultation (`repriseEnLigne` de l'iOS).
 *
 * ⚠️ Divergence déclarée : l'iOS pose `BandeauHorsLigne` dans chaque page, sous sa barre, et
 * grise chaque geste qui a besoin du serveur (`gesteEnLigne`) ; ici le bandeau est posé une
 * fois, au-dessus de l'espace, et les gestes restent actifs — la porte des lectures les
 * refuse avant qu'ils partent (« Vous êtes hors ligne… »). Le composant commun et les
 * gestes grisés viennent par la couche commune (ticket nommé dans la PR).
 */
@Composable
fun EspaceHorsLigne(consultation: Instant?, enLigne: Boolean, reprendre: () -> Unit, contenu: @Composable () -> Unit) {
    LaunchedEffect(consultation != null, enLigne) { if (consultation != null && enLigne) reprendre() }
    LifecycleResumeEffect(consultation, enLigne) {
        if (consultation != null && enLigne) reprendre()
        onPauseOrDispose {}
    }
    val visible = consultation != null || !enLigne
    Column(Modifier.fillMaxSize()) {
        if (visible) BandeauHorsLigne(consultation)
        Box(Modifier.weight(1f).then(if (visible) Modifier.consumeWindowInsets(WindowInsets.statusBars) else Modifier)) {
            contenu()
        }
    }
}

/**
 * Hors ligne, dit une fois : la résidence ouverte SANS réseau, sur sa dernière lecture —
 * d'où viennent les données et pourquoi rien n'agit ; ou l'appareil qui perd le réseau,
 * l'app ouverte — ce qui est affiché peut dater. Une seule annonce au lecteur d'écran.
 */
@Composable
private fun BandeauHorsLigne(consultation: Instant?) {
    val couleurs = LocalCouleurs.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .statusBarsPadding()
            .padding(horizontal = Mise.marge, vertical = 8.dp)
            .semantics(mergeDescendants = true) {}
            .testTag(REPERE_BANDEAU),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Filled.CloudOff, contentDescription = null, tint = couleurs.text, modifier = Modifier.size(16.dp))
            Text(
                if (consultation != null) {
                    stringResource(R.string.hors_ligne_a_jour, FormatsDuSite.dateCourte(consultation), FormatsDuSite.heure(consultation))
                } else {
                    stringResource(R.string.hors_ligne_peut_dater)
                },
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = couleurs.text,
                textAlign = TextAlign.Center,
            )
        }
        if (consultation != null) {
            Text(
                stringResource(R.string.hors_ligne_consultation),
                style = MaterialTheme.typography.labelSmall,
                color = couleurs.textMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Le repère du bandeau — l'identifiant d'accessibilité de l'iOS. */
const val REPERE_BANDEAU = "horsligne.bandeau"
