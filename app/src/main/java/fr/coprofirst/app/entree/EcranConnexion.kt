// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.entree

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalUriHandler
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
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.commun.BandeauInstallationSuspendue
import fr.coprofirst.app.commun.LogoResidence
import fr.coprofirst.app.commun.MessageErreur
import fr.coprofirst.app.ThemeCoproFirst
import fr.coprofirst.app.entree.vitrine.ContenuVitrine
import fr.coprofirst.app.session.Installation

/**
 * « Connectez-vous à votre espace », comme l'iOS (`ConnexionView`) : un seul écran,
 * la résidence d'abord, puis les identifiants ; un hôte jamais vu se confirme avant.
 */
@Composable
fun EcranConnexion(etat: EtatEntree, parcours: ActionsEntree) {
    var scanner by rememberSaveable { mutableStateOf(false) }
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
            ChampResidence(etat, parcours::saisir, scanner = { scanner = true })
            etat.aideDemo?.let { AideDemo(it) }
            etat.avisSortie?.let { AvisDeSortie(it) }
            ChampsIdentifiants(etat, parcours)
        }
    }
    if (scanner) {
        FeuilleScannerQR(fermer = { scanner = false }) { texte ->
            scanner = false
            parcours.lireQR(texte)
        }
    }
    etat.confirmation?.let { FeuilleConfirmationHote(it, parcours) }
}

/**
 * « Votre résidence » : l'adresse, l'attente puis la coche DANS le champ, et l'icône du QR
 * code de la fiche d'arrivée ; sous lui le nom de la résidence reconnue — ou la phrase de
 * l'échec. Aucun annuaire.
 */
@Composable
private fun ChampResidence(etat: EtatEntree, saisir: (String) -> Unit, scanner: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (etat.saisie.isEmpty()) focus.requestFocus() }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = etat.saisie,
            onValueChange = saisir,
            label = { Text(stringResource(R.string.connexion_residence)) },
            placeholder = { Text(stringResource(R.string.adresse_exemple)) },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EtatReconnaissance(etat.enCours, etat.reconnue)
                    IconButton(onClick = scanner, modifier = Modifier.testTag(Reperes.SCANNER)) {
                        Icon(
                            Icons.Filled.QrCodeScanner,
                            contentDescription = stringResource(R.string.connexion_scanner),
                            tint = LocalCouleurs.current.primary,
                        )
                    }
                }
            },
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
        // Comme l'iOS (`ChampResidence`) : sous le champ, l'échec dit en une phrase…
        etat.erreur?.let { MessageErreur(it) }
        // … ou le nom de la résidence reconnue.
        AnimatedVisibility(
            visible = etat.reconnue != null,
            enter = fadeIn(tween(200)) + expandVertically(tween(200)),
            exit = fadeOut(tween(120)) + shrinkVertically(tween(120)),
        ) {
            Column {
                NomReconnu(etat.reconnue)
                // L'installation reconnue est suspendue : le site le dit dès la connexion (#233).
                BandeauInstallationSuspendue(etat.reconnue, Modifier.padding(top = 8.dp))
            }
        }
    }
}

/**
 * Une sortie dont le serveur n'a pas été joint (#177), comme l'avis de l'iOS sur la
 * connexion (`parcours.avis`) : la session est effacée de l'appareil, le serveur la
 * révoquera à son expiration. Une information, pas une erreur — sur `neutreFond`.
 */
@Composable
private fun AvisDeSortie(avis: AvisSortie) {
    val couleurs = LocalCouleurs.current
    Row(
        Modifier.fillMaxWidth().background(couleurs.neutreFond, RoundedCornerShape(Mise.rayon)).padding(14.dp)
            .semantics(mergeDescendants = true) {}.testTag(REPERE_AVIS_SORTIE),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Filled.Info, contentDescription = null, tint = couleurs.text, modifier = Modifier.size(20.dp))
        Text(
            stringResource(
                when (avis) {
                    AvisSortie.SessionInjoignable -> R.string.sortie_injoignable
                    AvisSortie.ResidencesInjoignables -> R.string.sortie_injoignables
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = couleurs.text,
        )
    }
}

/** Le repère de l'avis de sortie, pour les tests d'interface. */
const val REPERE_AVIS_SORTIE = "connexion.avisSortie"

/** L'aide de la démo, comme l'iOS (`AideDemo`) : où lire le mot de passe public du rôle choisi. */
@Composable
private fun AideDemo(role: String) {
    val couleurs = LocalCouleurs.current
    val site = LocalUriHandler.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(couleurs.primaryLight, RoundedCornerShape(Mise.rayon))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.Info, contentDescription = null, tint = couleurs.primary, modifier = Modifier.size(20.dp))
            Text(stringResource(R.string.demo_aide, role), style = MaterialTheme.typography.bodyMedium, color = couleurs.text)
        }
        TextButton(onClick = { site.openUri(ContenuVitrine.PAGE_IDENTIFIANTS_DEMO) }) {
            Text(
                stringResource(R.string.demo_identifiants),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = couleurs.primary,
            )
        }
    }
}

@Composable
private fun NomReconnu(reconnue: Installation?) {
    val nom = reconnue?.nom.orEmpty()
    val annonce = stringResource(R.string.connexion_reconnue, nom)
    // Le logo de la résidence reconnue devant son nom, comme l'iOS (`ChampResidence`, #137).
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LogoResidence(reconnue, Modifier.size(20.dp))
        Text(
            nom,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.semantics { contentDescription = annonce },
        )
    }
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
                saisie = "residence.exemple.test",
                reconnue = Installation("https://residence.exemple.test", "Résidence d'exemple"),
            ),
            ActionsEntree.Inertes,
        )
    }
}
