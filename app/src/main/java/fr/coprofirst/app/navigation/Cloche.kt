// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.commun.FeuillePleine

/**
 * La cloche de la barre du haut, comme `ClocheNotifications` de l'iOS : sur chaque page de
 * la résidence, un toucher ouvre la page des notifications EN FEUILLE — l'écran que le
 * registre inscrit pour la page `notifications` (sans entrée de menu sur le site : la
 * cloche en est l'accès). Un point dit qu'une alerte attend ; le nombre, compté sur la liste
 * (`CompteurNonLues`), est dit au lecteur d'écran et sous le titre des notifications.
 */
object Cloche {
    const val ID_PAGE = "notifications"
    const val REPERE = "cloche"
    const val REPERE_POINT = "cloche.point"
    const val REPERE_OK = "notifications.ok"

    /** La page des notifications (`PagesParDefaut.horsMenu`, générée) ; `null` : pas de cloche. */
    val page: PageDeNavigation? get() = PagesParDefaut.horsMenu.firstOrNull { it.id == ID_PAGE }
}

/**
 * Le bouton de la cloche, à poser dans les actions de la barre du haut. `nonLues` : le nombre
 * noté pour la résidence (`CompteurNonLues.nombre`) — au-delà de zéro, le point de
 * `bell.badge` (teinte `danger`), qui paraît et s'en va en fondu, et « 12 non lues » dit par
 * TalkBack après « Notifications ».
 */
@Composable
fun BoutonCloche(page: PageDeNavigation, nonLues: Int, ouvrir: () -> Unit) {
    val couleurs = LocalCouleurs.current
    val etat = if (nonLues > 0) CompteurNonLues.phrase(nonLues) else null
    IconButton(onClick = ouvrir, modifier = Modifier.testTag(Cloche.REPERE).semantics { etat?.let { stateDescription = it } }) {
        BadgedBox(
            badge = {
                AnimatedVisibility(
                    visible = nonLues > 0,
                    enter = scaleIn(initialScale = 0.6f) + fadeIn(),
                    exit = scaleOut(targetScale = 0.6f) + fadeOut(),
                ) {
                    Badge(containerColor = couleurs.danger, modifier = Modifier.testTag(Cloche.REPERE_POINT))
                }
            },
        ) {
            Icon(Icons.Outlined.Notifications, contentDescription = page.titre, tint = couleurs.primary)
        }
    }
}

/**
 * La feuille des notifications, comme `.feuilleNotifications` de l'iOS : le titre de la page
 * et le sous-titre que son écran déclare, ses actions (sa propre barre), « OK » qui la ferme ;
 * puis l'écran du registre (`FeuillePleine`). `contexte` : le contexte de l'écran, pour la
 * barre de la feuille.
 */
@Composable
fun FeuilleNotifications(page: PageDeNavigation, contexte: (BarreDuHaut) -> ContexteDeMenu, fermer: () -> Unit) {
    val barre = remember { BarreDuHaut() }
    FeuillePleine(page.titre, Cloche.REPERE_OK, fermer, actions = { ActionsDeclarees(barre) }, sousTitre = barre.sousTitre) {
        RegistreDesMenus.Ecran(contexte(barre))
    }
}
