// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.entree

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.GppMaybe
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mouvement
import fr.coprofirst.app.charte.Typo
import fr.coprofirst.app.commun.BoutonContour
import fr.coprofirst.app.commun.BoutonPlein
import fr.coprofirst.app.session.Installation
import fr.coprofirst.app.session.ProtectionSession

/**
 * « Ouvrir avec votre empreinte ? », proposée une fois après la première connexion à une
 * résidence, comme l'iOS (`FaceIDFeuille`). Une fois la session gardée, elle dit ce qui la
 * protège VRAIMENT : la biométrie, ou le seul verrouillage de l'appareil, ou rien (#84).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeuilleGardeSession(residence: Installation, etat: EtatEntree, parcours: ActionsEntree) {
    val feuille = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val biometrie = rememberBiometrie()
    val toucher = LocalHapticFeedback.current
    LaunchedEffect(etat.issueGarde) {
        val issue = etat.issueGarde ?: return@LaunchedEffect
        val rassurant = issue is IssueGarde.Gardee && issue.protection == ProtectionSession.Biometrie
        toucher.performHapticFeedback(if (rassurant) HapticFeedbackType.Confirm else HapticFeedbackType.Reject)
    }
    ModalBottomSheet(onDismissRequest = parcours::fermerGarde, sheetState = feuille) {
        // Un fondu et un léger grossissement, jamais depuis rien — l'échelle d'animation du
        // système le réduit ou le supprime.
        AnimatedContent(
            targetState = etat.issueGarde,
            transitionSpec = {
                (fadeIn(tween(220, easing = Mouvement.sortie)) + scaleIn(tween(220, easing = Mouvement.sortie), initialScale = 0.96f))
                    .togetherWith(fadeOut(tween(110)))
            },
            label = "garde",
        ) { issue ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 16.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (issue == null) Question(residence, biometrie, etat.activationEnCours, parcours) else Resultat(residence, issue, biometrie, parcours)
            }
        }
    }
}

@Composable
private fun Question(residence: Installation, biometrie: Biometrie, enCours: Boolean, parcours: ActionsEntree) {
    Pictogramme(if (biometrie == Biometrie.Aucune) Icons.Filled.LockOpen else Icons.Filled.Fingerprint, LocalCouleurs.current.primary)
    Titre(stringResource(biometrie.question))
    val texte = biometrie.nom?.let { stringResource(R.string.garde_texte, stringResource(it), residence.nom) }
        ?: stringResource(R.string.garde_texte_sans, residence.nom)
    Explication(texte)
    BoutonPlein(R.string.garde_activer, Modifier.testTag(ReperesGarde.ACTIVER), actif = !enCours, enCours = enCours, onClick = parcours::activerGarde)
    BoutonContour(R.string.garde_plus_tard, Modifier.testTag(ReperesGarde.PLUS_TARD), onClick = parcours::fermerGarde)
}

/** Ce qui garde la session, dit sans détour : une protection moindre s'annonce comme telle. */
@Composable
private fun Resultat(residence: Installation, issue: IssueGarde, biometrie: Biometrie, parcours: ActionsEntree) {
    val couleurs = LocalCouleurs.current
    val nom = stringResource(biometrie.nom ?: R.string.biometrie_nom_generique)
    when (issue) {
        is IssueGarde.Gardee -> when (issue.protection) {
            ProtectionSession.Biometrie -> {
                Pictogramme(Icons.Filled.GppGood, couleurs.primary)
                Titre(stringResource(R.string.garde_protegee_titre, nom), Modifier.testTag(ReperesGarde.PROTECTION))
                Explication(stringResource(R.string.garde_protegee_texte, residence.nom, nom))
            }
            ProtectionSession.CodeAppareil -> {
                Pictogramme(Icons.Filled.Lock, couleurs.textMuted)
                Titre(stringResource(R.string.garde_code_titre), Modifier.testTag(ReperesGarde.PROTECTION))
                Explication(stringResource(R.string.garde_code_texte))
            }
            ProtectionSession.Aucune -> {
                Pictogramme(Icons.Filled.GppMaybe, couleurs.textMuted)
                Titre(stringResource(R.string.garde_aucune_titre), Modifier.testTag(ReperesGarde.PROTECTION))
                Explication(stringResource(R.string.garde_aucune_texte))
            }
        }
        IssueGarde.Echec -> {
            Pictogramme(Icons.Filled.GppBad, couleurs.textMuted)
            Titre(stringResource(R.string.garde_echec_titre), Modifier.testTag(ReperesGarde.PROTECTION))
            Explication(stringResource(R.string.garde_echec_texte, residence.nom))
        }
    }
    BoutonPlein(R.string.garde_continuer, Modifier.testTag(ReperesGarde.CONTINUER), onClick = parcours::fermerGarde)
}

@Composable
private fun Pictogramme(icone: ImageVector, teinte: Color) =
    Icon(icone, contentDescription = null, tint = teinte, modifier = Modifier.padding(top = 8.dp).size(56.dp))

@Composable
private fun Titre(texte: String, modifier: Modifier = Modifier) = Text(
    texte,
    style = Typo.titre(),
    textAlign = TextAlign.Center,
    modifier = modifier.semantics { heading() },
)

@Composable
private fun Explication(texte: String) = Text(
    texte,
    style = MaterialTheme.typography.bodyLarge,
    color = LocalCouleurs.current.textMuted,
    textAlign = TextAlign.Center,
)

/** Les repères de la feuille — les identifiants d'accessibilité de l'iOS (`faceid.*`). */
object ReperesGarde {
    const val ACTIVER = "faceid.activer"
    const val PLUS_TARD = "faceid.plusTard"
    const val PROTECTION = "faceid.protection"
    const val CONTINUER = "faceid.continuer"
}
