// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.montre

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import fr.coprofirst.montre.cloche.EcranAlerte
import fr.coprofirst.montre.cloche.EcranCloche
import fr.coprofirst.montre.cloche.LectureCloche

/** Deux écrans : la cloche, et le détail d'une alerte (un balayage vers la droite y revient). */
@Composable
fun NavigationMontre(residence: String, lecture: LectureCloche) {
    val navigation = rememberSwipeDismissableNavController()
    SwipeDismissableNavHost(navController = navigation, startDestination = CLOCHE) {
        composable(CLOCHE) { EcranCloche(residence, lecture) { id -> navigation.navigate("$ALERTE/$id") } }
        composable("$ALERTE/{id}", arguments = listOf(navArgument("id") { type = NavType.IntType })) { entree ->
            val id = entree.arguments?.getInt("id")
            // L'alerte se relit dans la cloche courante ; sortie de la cloche, l'écran se ferme.
            val alerte = (lecture as? LectureCloche.Lue)?.cloche?.alerte(id ?: -1)
            if (alerte != null) EcranAlerte(alerte) else LaunchedEffect(id) { navigation.popBackStack() }
        }
    }
}

private const val CLOCHE = "cloche"
private const val ALERTE = "alerte"
