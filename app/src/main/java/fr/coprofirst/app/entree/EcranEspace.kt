// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.entree

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.R
import fr.coprofirst.app.session.Installation

/**
 * L'espace d'une résidence ouverte. ⚠️ Divergence déclarée : l'iOS ouvre ici le fil
 * d'actualité et la barre de la résidence ; ils arrivent avec les lots suivants (#11).
 */
@Composable
fun EcranEspace(ouverte: Installation?, parcours: ActionsEntree) {
    CadreEcran(retour = null) { marges ->
        Column(
            modifier = Modifier
                .padding(marges)
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
                .widthIn(max = 480.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                ouverte?.nom.orEmpty(),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(stringResource(R.string.espace_connecte, ouverte?.nom.orEmpty()), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.espace_bientot),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = parcours::deconnecter) { Text(stringResource(R.string.espace_deconnecter)) }
        }
    }
}
