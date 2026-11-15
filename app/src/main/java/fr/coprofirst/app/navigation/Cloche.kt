// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.commun.FeuillePleine

/**
 * La cloche de la barre du haut, comme `ClocheNotifications` de l'iOS : sur chaque page de
 * la résidence, un toucher ouvre la page des notifications EN FEUILLE — l'écran que le
 * registre inscrit pour la page `notifications` (sans entrée de menu sur le site : la
 * cloche en est l'accès).
 *
 * ⚠️ Divergence déclarée : pas de point « non lues » sur la cloche. L'iOS le compte en
 * relisant toute la liste (GET /notifications) — aucune route du contrat ne sert ce nombre
 * seul ; il reste dit en tête de la page des notifications.
 */
object Cloche {
    const val ID_PAGE = "notifications"
    const val REPERE = "cloche"
    const val REPERE_OK = "notifications.ok"

    /** La page des notifications (`PagesParDefaut.horsMenu`, générée) ; `null` : pas de cloche. */
    val page: PageDeNavigation? get() = PagesParDefaut.horsMenu.firstOrNull { it.id == ID_PAGE }
}

/** Le bouton de la cloche, à poser dans les actions de la barre du haut. */
@Composable
fun BoutonCloche(page: PageDeNavigation, ouvrir: () -> Unit) {
    IconButton(onClick = ouvrir, modifier = Modifier.testTag(Cloche.REPERE)) {
        Icon(Icons.Outlined.Notifications, contentDescription = page.titre, tint = LocalCouleurs.current.primary)
    }
}

/**
 * La feuille des notifications, comme `.feuilleNotifications` de l'iOS : le titre de la page,
 * les actions que son écran déclare (sa propre barre), « OK » qui la ferme ; puis l'écran du
 * registre (`FeuillePleine`). `contexte` : le contexte de l'écran, pour la barre de la feuille.
 */
@Composable
fun FeuilleNotifications(page: PageDeNavigation, contexte: (BarreDuHaut) -> ContexteDeMenu, fermer: () -> Unit) {
    val barre = remember { BarreDuHaut() }
    FeuillePleine(page.titre, Cloche.REPERE_OK, fermer, actions = { ActionsDeclarees(barre) }) {
        RegistreDesMenus.Ecran(contexte(barre))
    }
}
