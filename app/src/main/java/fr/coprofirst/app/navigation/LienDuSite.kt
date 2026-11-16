// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.navigation

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
 * l'Android revient au fil, comme le demande #147 — et l'ancre d'un message (`#msg-N`) n'est
 * pas suivie, la fiche d'une affaire ne sachant pas encore s'y placer.
 */
object LienDuSite {
    /** Le fil : là où revient un lien qui ne vise rien que l'app porte. */
    val FIL = Destination.Page(LibellesBarre.ID_FIL)

    /** La route des affaires sur le site — celle d'une affaire est `/tickets/<id>`. */
    private const val ROUTE_AFFAIRES = "/tickets"
    private const val NATURE_AFFAIRE = "affaire"

    fun destination(chemin: String): Destination? {
        val segments = chemin.substringBefore('#').substringBefore('?').split('/').filter { it.isNotEmpty() }
        return when {
            segments.isEmpty() -> FIL
            segments.first() == "tickets" && segments.size == 2 ->
                segments[1].toIntOrNull()?.let { Destination.Element(NATURE_AFFAIRE, it.toString()) } ?: FIL
            segments.first() == "actualites" -> page(ROUTE_AFFAIRES) ?: FIL
            segments == listOf("auth", "connexion") -> null
            else -> page("/" + segments.joinToString("/")) ?: FIL
        }
    }

    private fun page(route: String): Destination.Page? =
        PagesParDefaut.toutes.firstOrNull { it.route == route }?.let { Destination.Page(it.id) }
}
