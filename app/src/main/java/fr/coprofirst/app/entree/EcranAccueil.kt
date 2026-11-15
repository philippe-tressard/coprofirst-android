// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.entree

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.R
import fr.coprofirst.app.ThemeCoproFirst
import fr.coprofirst.app.session.AdresseInstallation
import fr.coprofirst.app.session.Installation

/**
 * L'accueil : le nom, la devise, deux portes — ma résidence, la démonstration — et les
 * résidences déjà ouvertes sur l'appareil. Quand le texte grandit, l'écran défile.
 */
@Composable
fun EcranAccueil(etat: EtatEntree, parcours: ActionsEntree) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Text(
            stringResource(R.string.nom_app),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.accueil_devise),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1f).height(32.dp))
        Column(
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BoutonPlein(R.string.accueil_entrer, onClick = parcours::ouvrirAdresse)
            BoutonContour(
                R.string.accueil_demo,
                enCours = etat.enCours == AdresseInstallation.DEMO,
                onClick = parcours::entrerDemo,
            )
            etat.erreur?.let { MessageErreur(it) }
            if (etat.connues.isNotEmpty()) ResidencesConnues(etat.connues, parcours::rouvrir)
        }
    }
}

@Composable
private fun ResidencesConnues(connues: List<Installation>, rouvrir: (Installation) -> Unit) {
    Column(modifier = Modifier.padding(top = 20.dp)) {
        Text(
            stringResource(R.string.accueil_connues),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { heading() },
        )
        connues.forEach { installation ->
            ListItem(
                headlineContent = { Text(installation.nom) },
                supportingContent = { Text(installation.adresse?.enSaisie.orEmpty()) },
                modifier = Modifier.cliquable(stringResource(R.string.accueil_rouvrir)) { rouvrir(installation) },
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ApercuAccueil() {
    ThemeCoproFirst {
        EcranAccueil(
            EtatEntree(connues = listOf(Installation("https://residence.exemple.fr", "Résidence d'exemple"))),
            ActionsEntree.Inertes,
        )
    }
}
