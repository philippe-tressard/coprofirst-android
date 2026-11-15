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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.R
import fr.coprofirst.app.ThemeCoproFirst
import fr.coprofirst.app.session.Installation

/** La résidence reconnue : son nom et son adresse. La connexion viendra au lot suivant. */
@Composable
fun EcranResidence(installation: Installation, parcours: ActionsEntree) {
    CadreEcran(titre = R.string.residence_titre, retour = parcours::retour) { marges ->
        Column(
            modifier = Modifier
                .padding(marges)
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
                .widthIn(max = 480.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                installation.nom,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                installation.adresse?.enSaisie.orEmpty(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.residence_bientot),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 16.dp),
            )
            TextButton(onClick = { parcours.oublier(installation) }, modifier = Modifier.padding(top = 8.dp)) {
                Text(stringResource(R.string.residence_oublier))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ApercuResidence() {
    ThemeCoproFirst {
        EcranResidence(Installation("https://residence.exemple.fr", "Résidence d'exemple", "1.27.0"), ActionsEntree.Inertes)
    }
}
