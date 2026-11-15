// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.entree

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.R

/**
 * Les identifiants, sous la résidence, comme l'iOS : adresse électronique, mot de passe
 * (avec l'œil), « Mot de passe oublié ? », « Se connecter », « Demander l'accès ». Les
 * deux liens ouvrent les pages du site de la résidence reconnue.
 */
@Composable
fun ChampsIdentifiants(etat: EtatEntree, parcours: ActionsEntree) {
    val site = LocalUriHandler.current
    val racine = etat.reconnue?.adresse?.url
    val refuses = etat.erreurConnexion != null
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = etat.email,
            onValueChange = parcours::saisirEmail,
            label = { Text(stringResource(R.string.connexion_email)) },
            placeholder = { Text(stringResource(R.string.connexion_email_exemple)) },
            isError = refuses,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            ),
            modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.Username },
        )
        OutlinedTextField(
            value = etat.motDePasse,
            onValueChange = parcours::saisirMotDePasse,
            label = { Text(stringResource(R.string.connexion_mot_de_passe)) },
            placeholder = { Text(stringResource(R.string.connexion_mot_de_passe_exemple)) },
            isError = refuses,
            singleLine = true,
            visualTransformation = if (etat.voirMotDePasse) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = parcours::basculerVoirMotDePasse) {
                    Icon(
                        if (etat.voirMotDePasse) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = stringResource(
                            if (etat.voirMotDePasse) R.string.connexion_cacher_mot_de_passe else R.string.connexion_voir_mot_de_passe,
                        ),
                    )
                }
            },
            keyboardOptions = KeyboardOptions(
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Go,
            ),
            keyboardActions = KeyboardActions(onGo = { parcours.seConnecter() }),
            modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.Password },
        )
        TextButton(
            onClick = { racine?.let { site.openUri("$it/auth/mot-de-passe-oublie") } },
            enabled = racine != null,
            modifier = Modifier.align(Alignment.End),
        ) { Text(stringResource(R.string.connexion_oublie)) }
        etat.erreurConnexion?.let { MessageErreur(it) }
        Button(
            onClick = parcours::seConnecter,
            enabled = etat.connexionPossible,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (etat.connexionEnCours) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.connexion_valider))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.connexion_pas_de_compte),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { racine?.let { site.openUri("$it/auth/inscription") } }, enabled = racine != null) {
                Text(stringResource(R.string.connexion_demander_acces))
            }
        }
    }
}
