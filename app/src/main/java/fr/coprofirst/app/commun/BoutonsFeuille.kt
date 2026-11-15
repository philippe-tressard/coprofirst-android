// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.R

/**
 * Le pied d'une feuille de saisie, comme `BoutonsFeuille` de l'iOS (`PiedFormulaire` du
 * site) : le verbe de soumission (« Enregistrer » par défaut) en `BoutonPlein`, remplacé
 * par « Enregistrement… » pendant l'`envoi`, puis « Annuler ». `actif` : faux tant que le
 * formulaire n'est pas assez rempli pour partir ; `repere` : celui du bouton, pour les tests.
 *
 * ⚠️ Divergence déclarée : l'iOS les pose dans la barre de la feuille (Annuler à gauche,
 * le verbe à droite) ; une feuille d'Android n'a pas de barre — ils ferment la feuille,
 * sous le pouce, comme `FeuilleConfirmationHote`.
 */
@Composable
fun BoutonsFeuille(
    envoi: Boolean,
    repere: String,
    annuler: () -> Unit,
    modifier: Modifier = Modifier,
    @StringRes libelle: Int = R.string.feuille_enregistrer,
    actif: Boolean = true,
    envoyer: () -> Unit,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BoutonPlein(
            if (envoi) R.string.feuille_en_cours else libelle,
            Modifier.testTag(repere),
            actif = actif,
            enCours = envoi,
            onClick = envoyer,
        )
        TextButton(onClick = annuler, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.annuler))
        }
    }
}
