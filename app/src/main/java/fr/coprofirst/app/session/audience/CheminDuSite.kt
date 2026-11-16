// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.session.audience

import fr.coprofirst.app.navigation.PagesParDefaut

/**
 * Le chemin, sur le site, de l'écran que l'app montre (#29, coprofirst#2726) : ce que le site
 * enregistre (`window.location.pathname`, et `<chemin>#<onglet>` pour un onglet,
 * `trackTabView`). Les pages du site et des apps coïncident ainsi au tableau de bord — comme
 * l'iOS (`CheminDuSite`).
 *
 * Une page du menu se lit dans la table GÉNÉRÉE (`PagesParDefaut`), jamais écrite ici.
 */
object CheminDuSite {
    /** La page des affaires (`routes/(app)/tickets` du site). */
    private const val PAGE_AFFAIRES = "mes-demandes"

    /** La page des sondages : la Communauté (`routes/(app)/sondages`). */
    private const val PAGE_SONDAGES = "communaute"

    /**
     * Le profil n'a pas d'entrée de menu (`href: null` dans `pages.ts`), donc pas de route
     * générée : celle du site, `routes/(app)/profil`.
     */
    const val PROFIL = "/profil"

    /** La route d'une page du menu (`pages.ts`) : `communaute` → `/sondages`. */
    fun page(id: String): String? = PagesParDefaut.toutes.firstOrNull { it.id == id }?.route?.takeIf { it.isNotEmpty() }

    /** Un onglet d'une page : `/admin#telemetry`, comme `trackTabView` du site. */
    fun onglet(onglet: String, route: String): String = "$route#$onglet"

    /** Une affaire : `/tickets/123` (`routes/(app)/tickets/[id]`). */
    fun affaire(id: Int): String? = page(PAGE_AFFAIRES)?.let { "$it/$id" }

    /** Un sondage : `/sondages/12` (`routes/(app)/sondages/[id]`). */
    fun sondage(id: Int): String? = page(PAGE_SONDAGES)?.let { "$it/$id" }
}
