// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.entree

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.R
import fr.coprofirst.app.ThemeCoproFirst

/** L'adresse de la résidence, telle qu'elle figure sur la fiche d'arrivée. */
@Composable
fun EcranAdresse(etat: EtatEntree, parcours: ActionsEntree) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    CadreEcran(titre = R.string.adresse_titre, retour = parcours::retour) { marges ->
        Column(
            modifier = Modifier
                .padding(marges)
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
                .widthIn(max = 480.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            OutlinedTextField(
                value = etat.saisie,
                onValueChange = parcours::saisir,
                label = { Text(stringResource(R.string.adresse_champ)) },
                placeholder = { Text(stringResource(R.string.adresse_exemple)) },
                supportingText = {
                    val erreur = etat.erreur
                    if (erreur != null) MessageErreur(erreur) else Text(stringResource(R.string.adresse_aide))
                },
                isError = etat.erreur != null,
                singleLine = true,
                enabled = etat.enCours == null,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(onGo = { parcours.entrerSaisie() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            BoutonPlein(
                R.string.adresse_continuer,
                enCours = etat.enCours != null,
                actif = etat.saisie.isNotBlank(),
                onClick = parcours::entrerSaisie,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ApercuAdresse() {
    ThemeCoproFirst { EcranAdresse(EtatEntree(saisie = "residence.exemple.fr"), ActionsEntree.Inertes) }
}
