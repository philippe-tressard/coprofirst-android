// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.charte.Mise

/** La hauteur des boutons d'action : au-delà des 48 dp tactiles, confortable au pouce. */
private val HAUTEUR_BOUTON = 52.dp

/**
 * Le bouton principal d'un écran, comme le `.plein` de l'iOS : fond `primaryPlein`,
 * texte `surPlein`, toute la largeur. Pendant `enCours`, l'attente s'affiche à côté du
 * libellé, et le bouton garde sa taille.
 */
@Composable
fun BoutonPlein(
    @StringRes libelle: Int,
    modifier: Modifier = Modifier,
    actif: Boolean = true,
    enCours: Boolean = false,
    onClick: () -> Unit,
) {
    Plein(
        stringResource(libelle),
        LocalCouleurs.current.primaryPlein,
        modifier.fillMaxWidth().heightIn(min = HAUTEUR_BOUTON),
        actif,
        enCours,
        icone = null,
        onClick,
    )
}

/**
 * Le bouton plein d'un geste DÉFINITIF (supprimer), comme le `Button(role: .destructive)`
 * plein de l'iOS : fond `dangerPlein`, texte `surPlein`, `icone` avant le libellé (la
 * corbeille). À la largeur de son libellé : l'écran le place (l'iOS le cale à droite d'une
 * barre) ; `Modifier.fillMaxWidth()` lui donne toute la largeur.
 */
@Composable
fun BoutonPleinDestructif(
    libelle: String,
    modifier: Modifier = Modifier,
    actif: Boolean = true,
    icone: ImageVector? = null,
    onClick: () -> Unit,
) {
    Plein(libelle, LocalCouleurs.current.dangerPlein, modifier.heightIn(min = Mise.cible), actif, enCours = false, icone, onClick)
}

/**
 * LE bouton plein : le SEUL `Button` de Material de l'app (verifier-depot.sh) — un `Button`
 * ailleurs reprendrait les couleurs par défaut, illisibles au sombre. Le texte est `surPlein`
 * sur le `fond` de la charte que chaque variante choisit.
 */
@Composable
private fun Plein(
    libelle: String,
    fond: Color,
    modifier: Modifier,
    actif: Boolean,
    enCours: Boolean,
    icone: ImageVector?,
    onClick: () -> Unit,
) {
    val surPlein = LocalCouleurs.current.surPlein
    Button(
        onClick = onClick,
        enabled = actif && !enCours,
        colors = ButtonDefaults.buttonColors(containerColor = fond, contentColor = surPlein),
        modifier = modifier,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (enCours) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = surPlein)
            } else if (icone != null) {
                Icon(icone, contentDescription = null, modifier = Modifier.size(20.dp))
            }
            Text(libelle)
        }
    }
}

/** Le bouton secondaire (`.contour` de l'iOS), même gabarit que le principal. */
@Composable
fun BoutonContour(@StringRes libelle: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier.fillMaxWidth().heightIn(min = HAUTEUR_BOUTON)) {
        Text(stringResource(libelle))
    }
}
