// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.entree

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.ThemeCoproFirst
import fr.coprofirst.app.session.Installation

/**
 * « Connectez-vous à votre espace », comme l'iOS (`ConnexionView`) : un seul écran,
 * la résidence d'abord, puis les identifiants ; un hôte jamais vu se confirme avant.
 */
@Composable
fun EcranConnexion(etat: EtatEntree, parcours: ActionsEntree) {
    CadreEcran(retour = parcours::retour) { marges ->
        Column(
            modifier = Modifier
                .padding(marges)
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
                .widthIn(max = 480.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                stringResource(R.string.connexion_titre),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            ChampResidence(etat, parcours::saisir)
            ChampsIdentifiants(etat, parcours)
        }
    }
    etat.confirmation?.let { FeuilleConfirmationHote(it, parcours) }
}

/**
 * « Votre résidence » : l'adresse, l'attente puis la coche DANS le champ, et sous lui
 * le nom de la résidence reconnue — ou la phrase de l'échec. Aucun annuaire.
 */
@Composable
private fun ChampResidence(etat: EtatEntree, saisir: (String) -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (etat.saisie.isEmpty()) focus.requestFocus() }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = etat.saisie,
            onValueChange = saisir,
            label = { Text(stringResource(R.string.connexion_residence)) },
            placeholder = { Text(stringResource(R.string.adresse_exemple)) },
            trailingIcon = { EtatReconnaissance(etat.enCours, etat.reconnue) },
            supportingText = etat.erreur?.let { erreur -> { MessageErreur(erreur) } },
            isError = etat.erreur != null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next,
            ),
            modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag(Reperes.RESIDENCE),
        )
        // Le nom paraît sous le champ : un geste rare, qui confirme — fondu et déploiement,
        // 200 ms ease-out ; il disparaît plus vite qu'il n'arrive.
        AnimatedVisibility(
            visible = etat.reconnue != null,
            enter = fadeIn(tween(200)) + expandVertically(tween(200)),
            exit = fadeOut(tween(120)) + shrinkVertically(tween(120)),
        ) {
            NomReconnu(etat.reconnue)
        }
    }
}

@Composable
private fun NomReconnu(reconnue: Installation?) {
    val nom = reconnue?.nom.orEmpty()
    val annonce = stringResource(R.string.connexion_reconnue, nom)
    Text(
        nom,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        modifier = Modifier.semantics { contentDescription = annonce },
    )
}

@Composable
private fun EtatReconnaissance(enCours: Boolean, reconnue: Installation?) {
    when {
        enCours -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        reconnue != null -> Icon(
            Icons.Filled.CheckCircle,
            contentDescription = stringResource(R.string.connexion_coche),
            tint = LocalCouleurs.current.success,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ApercuConnexion() {
    ThemeCoproFirst {
        EcranConnexion(
            EtatEntree(
                ecran = EcranEntree.Connexion,
                saisie = "residence.exemple.fr",
                reconnue = Installation("https://residence.exemple.fr", "Résidence d'exemple"),
            ),
            ActionsEntree.Inertes,
        )
    }
}
