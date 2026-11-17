// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.Orientation
import fr.coprofirst.app.charte.Mise

/**
 * Comment la résidence se navigue (#69), comme l'iOS (`PresentationNavigation`, #221) : choisi
 * sur la LARGEUR disponible, jamais sur l'appareil — une tablette en multi-fenêtre étroite a la
 * barre du bas, un pliable déplié la barre latérale. Le seuil est celui du « grand écran » de
 * l'orientation libre (`Orientation.GRAND_ECRAN_DP`, lot 1).
 */
enum class PresentationNavigation {
    /** La barre du bas et « Plus » : le téléphone, une fenêtre étroite. */
    Barre,

    /** La barre latérale, toutes les pages : une fenêtre large. */
    Laterale;

    companion object {
        fun pour(largeur: Dp): PresentationNavigation =
            if (largeur >= Orientation.GRAND_ECRAN_DP.dp) Laterale else Barre
    }
}

/**
 * La barre latérale d'une fenêtre large (`BarreLaterale` de l'iOS) : TOUTES les pages ouvertes au
 * compte, dans l'ordre servi (`pages_order`), puis Profil et À propos — les mêmes droits que la
 * barre du bas (`pagesOuvertes`). `ouverte` : la page affichée, dite sélectionnée.
 * Repères : `barre.<id>`, ceux de l'iOS.
 */
@Composable
internal fun BarreLaterale(pages: List<PageDeNavigation>, ouverte: String?, choisir: (String) -> Unit) {
    val couleurs = LocalCouleurs.current
    PermanentDrawerSheet(Modifier.width(LARGEUR), drawerContainerColor = couleurs.surface) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 16.dp)) {
            val toutes = PagesDePlus.suivies(pages)
            toutes.forEachIndexed { rang, page ->
                // Profil et À propos, à part des pages de la résidence.
                if (rang == pages.size) HorizontalDivider(Modifier.padding(vertical = 8.dp), color = couleurs.border)
                NavigationDrawerItem(
                    label = { Text(page.libelle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    icon = { Icon(PagesDePlus.icone(page), contentDescription = null) },
                    selected = page.id == ouverte,
                    onClick = { choisir(page.id) },
                    modifier = Modifier.testTag("barre.${page.id}"),
                )
            }
        }
    }
}

/** La largeur de la barre latérale : les libellés du site y tiennent, la page garde le reste. */
private val LARGEUR = 260.dp

/**
 * Une fiche dans la colonne d'une fenêtre large (#69) : la page garde la barre du haut (son
 * titre, ses boutons, la cloche) ; la fiche pose dans SA colonne son titre et ce qu'elle
 * mettrait en barre (`barre`), comme `dansLaColonneDeFiche` de l'iOS.
 */
@Composable
internal fun FicheEnColonne(titre: String, barre: BarreDuHaut, contenu: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = Mise.cible).padding(start = Mise.marge, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EnTeteDeBarre(barre.titre ?: titre, barre.sousTitre, Modifier.weight(1f).semantics { heading() })
            ActionsDeclarees(barre)
        }
        HorizontalDivider(color = LocalCouleurs.current.border)
        Box(Modifier.weight(1f)) { contenu() }
    }
}
