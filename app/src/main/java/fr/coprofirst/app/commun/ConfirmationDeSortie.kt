// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.session.Installation
import fr.coprofirst.app.session.RolesConnus

/** Les deux gestes de sortie, comme l'iOS (`GesteDeSortie`). */
sealed interface GesteDeSortie {
    data class Deconnecter(val nomResidence: String) : GesteDeSortie
    data object OublierTout : GesteDeSortie
}

/** Ce qu'un écran peut demander pour sortir : se déconnecter, ou tout oublier. */
interface GestesDeSortie {
    /** Se déconnecter de la résidence ouverte ; l'app revient à l'écran de connexion. */
    fun deconnecter()

    /** Fermer chaque session, oublier les résidences retenues : l'app revient à son accueil, comme neuve. */
    fun oublierTout()

    /**
     * Quitter la résidence ouverte SANS se déconnecter (#180, `Parcours.changer(pour:)` de
     * l'iOS) : la connexion, pré-remplie pour `installation` — sa session gardée se rouvre
     * par la biométrie ; `null` : « Une autre résidence », la connexion vierge.
     */
    fun changerDeResidence(installation: Installation?) = Unit

    /** Les résidences retenues sur l'appareil, avec le statut et les rôles qu'on y a. */
    fun residencesConnues(): List<ResidenceConnue> = emptyList()

    /** Le statut et les rôles du compte dans la résidence ouverte, relus : retenus pour la feuille. */
    fun noterRoles(installation: Installation, statut: String?, roles: List<String>) = Unit
}

/** Une résidence retenue, et ce que le compte y est (`null` : jamais relu). */
data class ResidenceConnue(val installation: Installation, val roles: RolesConnus?)

/**
 * La confirmation d'un geste de sortie, comme l'iOS (`confirmationDeSortie`) : titre,
 * message, bouton destructeur (repère `sortie.confirmer`) et « Annuler ». Le geste ne
 * part qu'au bouton.
 */
@Composable
fun ConfirmationDeSortie(geste: GesteDeSortie?, sortie: GestesDeSortie, fermer: () -> Unit) {
    geste ?: return
    val danger = LocalCouleurs.current.danger
    val (titre, message, bouton) = when (geste) {
        is GesteDeSortie.Deconnecter -> Triple(
            stringResource(R.string.sortie_deconnecter_titre, geste.nomResidence),
            stringResource(R.string.sortie_deconnecter_message),
            stringResource(R.string.se_deconnecter),
        )
        GesteDeSortie.OublierTout -> Triple(
            stringResource(R.string.sortie_oublier_titre),
            stringResource(R.string.sortie_oublier_message),
            stringResource(R.string.sortie_oublier_bouton),
        )
    }
    AlertDialog(
        onDismissRequest = fermer,
        title = { Text(titre) },
        text = { Text(message) },
        confirmButton = {
            TextButton(
                onClick = {
                    fermer()
                    if (geste is GesteDeSortie.Deconnecter) sortie.deconnecter() else sortie.oublierTout()
                },
                modifier = Modifier.testTag("sortie.confirmer"),
            ) { Text(bouton, color = danger, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = fermer) { Text(stringResource(R.string.annuler)) } },
    )
}
