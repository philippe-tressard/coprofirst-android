// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.entree

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.ThemeCoproFirst
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.charte.Typo
import fr.coprofirst.app.commun.BoutonPlein
import fr.coprofirst.app.commun.LogoResidence
import fr.coprofirst.app.commun.MessageErreur
import fr.coprofirst.app.partage.R as RCommun
import fr.coprofirst.app.session.Installation
import fr.coprofirst.app.session.LecturesGardees

/**
 * Le retour d'un habitué dont la session est gardée, comme l'iOS (`RetourView`) : la
 * résidence, et la biométrie demandée dès l'arrivée — puis le fil. Annulée, elle se
 * redemande d'un toucher ; le mot de passe reste à côté. Une session que le serveur ne
 * reconnaît plus mène à la connexion, adresse et courriel pré-remplis. En tête, le logo de la
 * résidence (`LogoResidence`, #137).
 */
@Composable
fun EcranRetour(etat: EtatEntree, parcours: ActionsEntree) {
    val residence = etat.reconnue ?: return
    val biometrie = rememberBiometrie()
    LaunchedEffect(residence.url) { parcours.ouvrirDOffice() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        LogoResidence(residence, Modifier.size(96.dp))
        Spacer(Modifier.height(16.dp))
        Text(
            residence.nom,
            style = Typo.titre(MaterialTheme.typography.displaySmall),
            color = LocalCouleurs.current.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() }.testTag(ReperesRetour.RESIDENCE),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            residence.adresse?.enSaisie.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = LocalCouleurs.current.textMuted,
        )
        Spacer(Modifier.weight(1f).height(32.dp))
        Column(
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (etat.lecturePerimee) {
                MessageErreur(
                    LecturesGardees.JOURS_MAX.toInt().let { pluralStringResource(RCommun.plurals.hors_ligne_perimee, it, it) },
                    modifier = Modifier.testTag(ReperesRetour.LECTURE_PERIMEE),
                )
            } else {
                etat.erreurReprise?.let { MessageErreur(it) }
            }
            BoutonPlein(
                biometrie.ouvrir,
                modifier = Modifier.testTag(ReperesRetour.OUVRIR),
                enCours = etat.repriseEnCours,
                actif = !etat.repriseEnCours,
                onClick = parcours::ouvrirSessionGardee,
            )
            TextButton(
                onClick = parcours::utiliserMotDePasse,
                modifier = Modifier.heightIn(min = Mise.cible).testTag(ReperesRetour.MOT_DE_PASSE),
            ) {
                Text(stringResource(R.string.retour_mot_de_passe))
            }
        }
    }
}

/** Les repères de l'écran de retour — les identifiants d'accessibilité de l'iOS. */
object ReperesRetour {
    const val RESIDENCE = "retour.residence"
    const val OUVRIR = "retour.ouvrir"
    const val MOT_DE_PASSE = "retour.motDePasse"
    const val LECTURE_PERIMEE = "retour.lecturePerimee"
}

@Preview(showBackground = true)
@Composable
private fun ApercuRetour() {
    ThemeCoproFirst {
        EcranRetour(
            EtatEntree(ecran = EcranEntree.Retour, reconnue = Installation("https://residence.exemple.test", "Les Glycines")),
            ActionsEntree.Inertes,
        )
    }
}
