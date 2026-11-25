// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.navigation

import fr.coprofirst.api.modeles.UserRead

/** Un onglet d'une page du site (`OngletsDuSite`, générée). */
data class OngletDuSite(val id: String, val libelle: String, val reserve: ReserveOnglet?, val ouvertOperateur: Boolean)

/** Les réserves d'un onglet, celles du site (`reserveAdmise`). */
enum class ReserveOnglet { ProprioOuCS, NonLocataire, Operateur, NonGestionnaire }

/**
 * Ce que l'utilisateur est, pour juger d'une page ou d'un onglet — comme l'iOS
 * (`DroitsOnglet`). Qui voit quelle page est SERVI (`acces_menu`, #333) ; pour les onglets,
 * les populations sont GÉNÉRÉES (`OngletsDuSite.roles…`, `statuts…`) et seule la façon de
 * les lire est écrite ici. ⚠️ Masquer ne protège rien : le serveur tranche ; cela évite de
 * tendre une entrée qui mène à un refus.
 */
data class DroitsOnglet(
    val administrateur: Boolean = false,
    val operateur: Boolean = false,
    val proprioOuCS: Boolean = false,
    val locataire: Boolean = false,
    val gestionnaire: Boolean = false,
    /** Conseil syndical ou administration : `est_moderateur` SERVI, jamais déduit des rôles. */
    val cs: Boolean = false,
    /**
     * Les publics du menu que le compte voit (`acces_menu` de GET /auth/me, #333) : une page
     * s'affiche si son public (`PageDeNavigation.acces`) en est. Avant la relecture du compte, ou
     * pour un compte gardé qui ne le porte pas encore, les pages de tous seulement.
     */
    val accesMenu: Set<String> = setOf(PagesOuvertes.TOUS),
    /** Le compte lui-même (`id` de GET /auth/me) : on ne signale ni ne masque ce qu'on a écrit (#282). */
    val compte: Int? = null,
) {
    companion object {
        /** Depuis le compte servi par GET /auth/me : `roles` (ou `role` seul), `statut`, `est_moderateur`, `acces_menu`. */
        fun depuis(moi: UserRead): DroitsOnglet = depuis(
            roles = moi.roles.orEmpty(),
            role = moi.role.value,
            statut = moi.statut.value,
            estModerateur = moi.estModerateur,
            accesMenu = moi.accesMenu?.toSet() ?: setOf(PagesOuvertes.TOUS),
        ).copy(compte = moi.id)

        fun depuis(
            roles: List<String>,
            role: String?,
            statut: String?,
            estModerateur: Boolean?,
            accesMenu: Set<String> = setOf(PagesOuvertes.TOUS),
        ): DroitsOnglet {
            val portes = (if (roles.isEmpty()) listOfNotNull(role) else roles).toSet()
            val s = statut.orEmpty()
            return DroitsOnglet(
                administrateur = portes.any { it in OngletsDuSite.rolesAdministrateur },
                operateur = portes.any { it in OngletsDuSite.rolesOperateur },
                proprioOuCS = portes.any { it in OngletsDuSite.rolesProprietaire } || estModerateur == true,
                locataire = s in OngletsDuSite.statutsLocataire,
                gestionnaire = s in OngletsDuSite.statutsGestionnaire,
                cs = estModerateur == true,
                accesMenu = accesMenu,
            )
        }
    }
}

/**
 * Les pages qu'un compte ouvre dans la barre, comme l'iOS (`PagesOuvertes`) et `pageAuMenu` du
 * site (#333) : une page s'affiche si son public (`acces`, généré de la table `pages`) est
 * dans `acces_menu` servi — aucun rôle ni statut lu ici. Une page inconnue reste fermée.
 */
object PagesOuvertes {
    /** Le public ouvert à tous les comptes (`acces` de la table `pages`). */
    const val TOUS = "tous"

    fun filtrer(pages: List<PageDeNavigation>, droits: DroitsOnglet) = pages.filter { ouverte(it, droits) }

    fun ouverte(page: PageDeNavigation, droits: DroitsOnglet): Boolean = page.acces != null && page.acces in droits.accesMenu

    /** Par l'identifiant d'une page du menu (un écran à onglets) : son public généré. */
    fun ouverte(page: String, droits: DroitsOnglet): Boolean =
        PagesParDefaut.toutes.firstOrNull { it.id == page }?.let { ouverte(it, droits) } ?: false
}

/** Les onglets qu'un compte ouvre sur une page, dans l'ordre du site (`ongletsOuverts`). */
fun OngletsDuSite.ouverts(page: String, droits: DroitsOnglet): List<OngletDuSite> {
    val reserveeAdmin = page in pagesReserveesAdmin
    return (parPage[page] ?: emptyList()).filter { onglet ->
        admise(onglet.reserve, droits) && (!reserveeAdmin || droits.administrateur || (droits.operateur && onglet.ouvertOperateur))
    }
}

private fun admise(reserve: ReserveOnglet?, droits: DroitsOnglet): Boolean = when (reserve) {
    null -> true
    ReserveOnglet.ProprioOuCS -> droits.proprioOuCS
    ReserveOnglet.NonLocataire -> !droits.locataire
    ReserveOnglet.Operateur -> droits.operateur
    ReserveOnglet.NonGestionnaire -> !droits.gestionnaire
}
