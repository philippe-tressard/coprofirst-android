// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.navigation

import fr.coprofirst.app.session.audience.CheminDuSite

/**
 * Le chemin d'un lien du site → l'écran qu'il vise dans la résidence (#147), comme
 * `LienUniversel` de l'iOS. L'hôte, la résidence et sa connexion sont l'affaire de l'entrée
 * (#7, `ParcoursEntree.suivreLien`) ; ici, seulement le chemin :
 *
 * | Chemin | Destination |
 * |---|---|
 * | `/` | le fil |
 * | `/tickets/<id>` (`#msg-N` ignoré) | la fiche de l'affaire |
 * | `/actualites[/…]` | la page des affaires (« les actualités sont des affaires », coprofirst) |
 * | `/auth/connexion` | `null` : la connexion, que l'entrée montre |
 * | la route d'une page du menu | cette page (table GÉNÉRÉE `PagesParDefaut`, jamais une liste écrite ici) |
 * | tout autre chemin | le fil |
 *
 * ⚠️ Divergence déclarée : l'iOS ouvre un chemin inconnu dans le navigateur (`.navigateur`) ;
 * l'Android revient au fil, comme le demande #147. L'ancre d'un message (`#msg-N`) n'est pas
 * suivie, comme l'iOS, qui la lit sans faire défiler la fiche jusqu'à lui.
 */
object LienDuSite {
    /** Le fil : là où revient un lien qui ne vise rien que l'app porte. */
    val FIL = Destination.Page(LibellesBarre.ID_FIL)

    private const val NATURE_AFFAIRE = "affaire"

    fun destination(chemin: String): Destination? {
        val segments = chemin.substringBefore('#').substringBefore('?').split('/').filter { it.isNotEmpty() }
        // La route des affaires (`/tickets`, celle d'une affaire `/tickets/<id>`) : la table générée.
        val affaires = CheminDuSite.affaires
        return when {
            segments.isEmpty() -> FIL
            affaires != null && segments.size == 2 && "/" + segments.first() == affaires ->
                segments[1].toIntOrNull()?.let { Destination.Element(NATURE_AFFAIRE, it.toString()) } ?: FIL
            segments.first() == "actualites" -> affaires?.let(::page) ?: FIL
            segments == listOf("auth", "connexion") -> null
            else -> page("/" + segments.joinToString("/")) ?: FIL
        }
    }

    private fun page(route: String): Destination.Page? =
        PagesParDefaut.toutes.firstOrNull { it.route == route }?.let { Destination.Page(it.id) }
}
