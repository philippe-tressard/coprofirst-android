// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.espacecs

import fr.coprofirst.api.modeles.AccesAdminOut
import fr.coprofirst.api.routes.AccesApi
import fr.coprofirst.app.commun.acces.TypeAcces
import fr.coprofirst.app.commun.texte.Recherche
import fr.coprofirst.app.session.SessionOuverte
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Ce que l'onglet « Badges & télécommandes » demande à la résidence, comme l'iOS
 * (`ServiceBadgesCS`) : le parc de la copropriété, badges Vigik et télécommandes confondus —
 * un RÉFÉRENTIEL qu'on consulte. Le serveur refuse ces routes à qui n'est pas du conseil.
 */
interface ServiceBadges {
    /** GET /acces/admin/vigiks et /acces/admin/telecommandes. */
    suspend fun parc(): List<AccesDuParc>
}

class ServiceBadgesApi(private val session: SessionOuverte) : ServiceBadges {
    override suspend fun parc(): List<AccesDuParc> = coroutineScope {
        val vigiks = async { session.appeler { b, c -> AccesApi(b, c).getAccesAdminVigiks() } }
        val telecommandes = async { session.appeler { b, c -> AccesApi(b, c).getAccesAdminTelecommandes() } }
        vigiks.await().map { it.lu(TypeAcces.Vigik) } + telecommandes.await().map { it.lu(TypeAcces.Telecommande) }
    }
}

/** Un badge ou une télécommande de la copropriété (`AccesAdminOut`). */
data class AccesDuParc(
    val type: TypeAcces,
    val numero: Int,
    val code: String,
    val porteurNom: String,
    /**
     * Le rang du porteur parmi tous les comptes, nom PUIS prénom, comparable entre Vigik et
     * télécommandes : le tri « par porteur » le lit, jamais `porteurNom` (qui commence par
     * le prénom). Sans porteur (`porteurId` nul) : en dernier.
     */
    val porteurRang: Int,
    val porteurId: Int? = null,
    val lotLibelle: String? = null,
    val statut: String,
    val chezLocataire: Boolean = false,
    /** Ce qu'il ouvre, nommé par le serveur (`perimetre_libelle`) ; `""` : rien à dire. */
    val perimetreLibelle: String = "",
) {
    /** L'identifiant d'une ligne, et son repère : `vigik-1`, `telecommande-3`. */
    val id: String get() = "${type.code}-$numero"

    /** Ce que l'accès ouvre, à montrer : le lieu servi, `null` s'il est vide. */
    val lieu: String? get() = perimetreLibelle.takeIf { it.isNotBlank() }
}

/**
 * La liste montrée : le type retenu (`null` : tous), la recherche sur ce que la ligne
 * affiche (code, porteur, lot), puis l'ordre « par porteur » du site — le rang servi, les
 * lignes sans porteur en dernier.
 */
fun List<AccesDuParc>.filtrer(type: TypeAcces?, recherche: String): List<AccesDuParc> =
    filter { (type == null || it.type == type) && Recherche.contientTousLesMots(listOf(it.code, it.porteurNom, it.lotLibelle), recherche) }
        .sortedWith(compareBy({ if (it.porteurId == null) 1 else 0 }, { it.porteurRang }))

internal fun AccesAdminOut.lu(type: TypeAcces) = AccesDuParc(
    type, id, code, porteurNom, porteurRang, porteurId, lotLibelle, statut.value, chezLocataire, perimetreLibelle,
)
