// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.charte.Mise
import fr.coprofirst.app.charte.Typo
import kotlinx.coroutines.launch

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
 * registre. `contexte` : le contexte de l'écran, pour la barre de la feuille.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeuilleNotifications(page: PageDeNavigation, contexte: (BarreDuHaut) -> ContexteDeMenu, fermer: () -> Unit) {
    val etat = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val portee = rememberCoroutineScope()
    val barre = remember { BarreDuHaut() }
    val couleurs = LocalCouleurs.current
    ModalBottomSheet(onDismissRequest = fermer, sheetState = etat, containerColor = couleurs.bg) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(start = Mise.marge, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    page.titre,
                    style = Typo.titre(),
                    color = couleurs.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                ActionsDeclarees(barre)
                TextButton(
                    onClick = { portee.launch { etat.hide() }.invokeOnCompletion { fermer() } },
                    modifier = Modifier.testTag(Cloche.REPERE_OK),
                ) {
                    Text(stringResource(R.string.cloche_ok), fontWeight = FontWeight.SemiBold)
                }
            }
            Box(Modifier.weight(1f)) { RegistreDesMenus.Ecran(contexte(barre)) }
        }
    }
}
