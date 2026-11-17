// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.commun.miseenpage.CoupeDeFenetre

/**
 * Les INTERCALAIRES du carnet (#242), sur la tranche droite d'un pliable déplié, comme l'iOS
 * (coprofirst-ios#119) : toutes les pages ouvertes au compte, dans l'ordre servi, puis Profil et
 * À propos — à la place de la barre du bas et de la barre latérale. La page ouverte y est choisie.
 * Repères : `intercalaire.<id>`.
 */
@Composable
internal fun Intercalaires(pages: List<PageDeNavigation>, ouverte: String?, choisir: (String) -> Unit) {
    NavigationRail(Modifier.fillMaxHeight(), containerColor = LocalCouleurs.current.surface) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            PagesDePlus.suivies(pages).forEach { page ->
                NavigationRailItem(
                    selected = page.id == ouverte,
                    onClick = { choisir(page.id) },
                    icon = { Icon(PagesDePlus.icone(page), contentDescription = null) },
                    label = { Text(page.libelle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.testTag("intercalaire.${page.id}"),
                )
            }
        }
    }
}

/**
 * Le marque-page de la résidence ouverte (#242) : à la PREMIÈRE ouverture de l'écran, la page où
 * on avait refermé est reprise (`reprendre`) ; ensuite, chaque page ouverte est retenue. Une
 * reconstruction (rotation, pli) ne reprend pas : l'écran a déjà sauvé sa place. Rien hors d'un
 * appareil pliable (`MarquesPages` n'y écrit ni n'y lit rien).
 */
@Composable
internal fun MarquePageDeLaResidence(residence: String, actuelle: MarquePage, reprendre: (MarquePage) -> Unit) {
    val marques = LocalMarquesPages.current ?: return
    var repris by rememberSaveable(residence) { mutableStateOf(false) }
    LaunchedEffect(residence) {
        if (!repris) {
            marques.lire(residence)?.let(reprendre)
            repris = true
        }
    }
    LaunchedEffect(residence, actuelle, repris) { if (repris) marques.ecrire(residence, actuelle) }
}

/** Le pli vertical d'un livre : la double page se coupe à sa position. */
internal fun Posture.coupe(): CoupeDeFenetre? = pli?.takeIf { enLivre }?.let { CoupeDeFenetre(it.gauche, it.droite) }
