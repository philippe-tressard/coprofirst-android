// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.entree

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.commun.BoutonContour
import fr.coprofirst.app.commun.BoutonPlein
import fr.coprofirst.app.ThemeCoproFirst

/**
 * L'accueil, comme celui de l'iOS (`AccueilView`) : le logo, le nom, la devise, trois portes —
 * ma résidence, la démonstration, et « En savoir plus » (la vitrine, #10). Quand le texte
 * grandit, l'écran défile.
 */
@Composable
fun EcranAccueil(parcours: ActionsEntree) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        // Le médaillon de l'iOS : le logo de CoproFirst, décoratif (le nom suit).
        Image(painterResource(R.drawable.logo), contentDescription = null, modifier = Modifier.size(112.dp))
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.nom_app),
            style = MaterialTheme.typography.displaySmall,
            color = LocalCouleurs.current.primary,
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
            BoutonPlein(R.string.accueil_entrer, onClick = parcours::entrer)
            BoutonContour(R.string.accueil_demo, onClick = parcours::entrerDemo)
            TextButton(
                onClick = parcours::enSavoirPlus,
                modifier = Modifier.fillMaxWidth().heightIn(min = Mise.cible).testTag("accueil.enSavoirPlus"),
            ) {
                Text(
                    stringResource(R.string.accueil_en_savoir_plus),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                    color = LocalCouleurs.current.primary,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ApercuAccueil() {
    ThemeCoproFirst { EcranAccueil(ActionsEntree.Inertes) }
}
