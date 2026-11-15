// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.navigation

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable

/**
 * Où un écran peut mener dans la résidence ouverte — la porte de l'iOS
 * (`Navigation/Porte/Destination.swift`). Un écran ne l'ouvre que par `contexte.ouvrir`,
 * jamais en touchant à la barre lui-même.
 *
 * L'adresse de la résidence et le navigateur intégré de l'iOS (`adresse`, `.navigateur`,
 * `.connexion`) servent aux liens d'application : ils arrivent avec eux (#7).
 */
sealed interface Destination {
    /** Une page du menu, par son identifiant (`PagesParDefaut`), ou une entrée fixe de « Plus ». */
    data class Page(val id: String) : Destination

    /**
     * Un élément précis, par sa nature (`affaire`, `sondage`… — celles du fil) et son
     * identifiant : la fiche que le menu de cette nature déclare (`RegistreDesMenus.fiches`).
     */
    data class Element(val nature: String, val id: String) : Destination

    /** La cloche : la liste des alertes, en feuille. */
    data object Notifications : Destination
}

/**
 * La fiche d'un élément, déclarée par son menu au registre : son titre (la barre du haut)
 * et son écran, qui reçoit le contexte de la résidence et l'identifiant de l'élément.
 */
class FicheDeMenu(@StringRes val titre: Int, val ecran: @Composable (ContexteDeMenu, String) -> Unit)

/** Où la porte pose une destination — `Aiguillage` de l'iOS. */
sealed interface Aiguillage {
    data class Onglet(val id: String) : Aiguillage

    data class PageDePlus(val id: String) : Aiguillage

    data class Fiche(val element: Destination.Element) : Aiguillage

    data object Cloche : Aiguillage

    /** Rien ne la porte dans l'app : l'appelant garde son repli (« Voir sur le site »). */
    data object Aucun : Aiguillage

    companion object {
        /**
         * ⚠️ Deux écarts déclarés avec l'iOS (#85) : une page de « Plus » s'y OUVRE (l'iOS
         * n'ouvre que l'onglet « Plus ») ; une page que le compte ne voit pas ne mène nulle
         * part (l'iOS revient au fil) — l'appelant le sait par `ouvrir` et garde son repli.
         * Les liens d'application (#7) reviendront au fil d'eux-mêmes, comme l'iOS.
         *
         * @param barre les pages de la barre, dans leur ordre ; @param plus celles de « Plus » ;
         * @param fiches les natures qu'un menu déclare ouvrir ; @param cloche la page des alertes existe.
         */
        fun aiguiller(
            destination: Destination,
            barre: List<String>,
            plus: List<String>,
            fiches: Set<String>,
            cloche: Boolean,
        ): Aiguillage = when (destination) {
            is Destination.Page -> when {
                destination.id in barre -> Onglet(destination.id)
                destination.id in plus || PagesDePlus.fixe(destination.id) != null -> PageDePlus(destination.id)
                else -> Aucun
            }
            is Destination.Element -> if (destination.nature in fiches) Fiche(destination) else Aucun
            Destination.Notifications -> if (cloche) Cloche else Aucun
        }
    }
}
