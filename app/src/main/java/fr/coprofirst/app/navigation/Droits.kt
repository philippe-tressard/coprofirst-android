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
 * (`DroitsOnglet`). Les populations sont GÉNÉRÉES (`OngletsDuSite.roles…`, `statuts…`) ;
 * seule la façon de les lire est écrite ici. ⚠️ Masquer ne protège rien : le serveur
 * tranche ; cela évite de tendre une entrée qui mène à un refus.
 */
data class DroitsOnglet(
    val administrateur: Boolean = false,
    val operateur: Boolean = false,
    val proprioOuCS: Boolean = false,
    val locataire: Boolean = false,
    val gestionnaire: Boolean = false,
    /** Conseil syndical ou administration : `est_moderateur` SERVI, jamais déduit des rôles. */
    val cs: Boolean = false,
    /** Le compte technique : administrateur, sans lot ni conseil. */
    val adminSeul: Boolean = false,
    /** Un proche aidant : la page Délégations lui est ouverte. */
    val aidant: Boolean = false,
) {
    /** Les outils du conseil (Prestataires, Espace CS) : modérateur, et pas le compte technique. */
    val outilDuConseil: Boolean get() = cs && !adminSeul

    companion object {
        /** Depuis le compte servi par GET /auth/me : `roles` (ou `role` seul), `statut`, `est_moderateur`. */
        fun depuis(moi: UserRead): DroitsOnglet = depuis(
            roles = moi.roles.orEmpty(),
            role = moi.role.value,
            statut = moi.statut.value,
            estModerateur = moi.estModerateur,
            delegationsAidant = moi.delegationsAidant?.size ?: 0,
        )

        fun depuis(roles: List<String>, role: String?, statut: String?, estModerateur: Boolean?, delegationsAidant: Int = 0): DroitsOnglet {
            val portes = (if (roles.isEmpty()) listOfNotNull(role) else roles).toSet()
            val s = statut.orEmpty()
            return DroitsOnglet(
                administrateur = portes.any { it in OngletsDuSite.rolesAdministrateur },
                operateur = portes.any { it in OngletsDuSite.rolesOperateur },
                proprioOuCS = portes.any { it in OngletsDuSite.rolesProprietaire } || estModerateur == true,
                locataire = s in OngletsDuSite.statutsLocataire,
                gestionnaire = s in OngletsDuSite.statutsGestionnaire,
                cs = estModerateur == true,
                adminSeul = portes.any { it in OngletsDuSite.rolesAdminSeul } && portes.none { it in OngletsDuSite.rolesSansAdminSeul },
                aidant = s == STATUT_AIDANT || delegationsAidant > 0,
            )
        }

        /** Le statut d'un proche aidant (`StatutUtilisateur.aidant` du contrat). */
        private const val STATUT_AIDANT = "aidant"
    }
}

/** Les pages qu'un compte ouvre dans la barre, comme l'iOS (`PagesOuvertes`). */
object PagesOuvertes {
    fun filtrer(pages: List<PageDeNavigation>, droits: DroitsOnglet) = pages.filter { ouverte(it.id, droits) }

    fun ouverte(page: String, droits: DroitsOnglet): Boolean = when (page) {
        "prestataires", "espace-cs" -> droits.outilDuConseil
        "delegations" -> droits.outilDuConseil || droits.aidant
        "admin" -> droits.administrateur || droits.operateur
        else -> true
    }
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
