// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.delegations

import fr.coprofirst.api.modeles.DelegationCreate
import fr.coprofirst.api.modeles.DelegationRead
import fr.coprofirst.api.modeles.UtilisateurAdminRead
import fr.coprofirst.api.routes.AdminApi
import fr.coprofirst.api.routes.DlgationsAidantApi
import fr.coprofirst.app.commun.LibelleTeinte
import fr.coprofirst.app.commun.formats.FormatsDuSite
import fr.coprofirst.app.session.SessionOuverte
import java.time.LocalDate

/**
 * Ce que « Délégations aidant » demande à la résidence (#25), comme l'iOS
 * (`ServiceDelegations`). Aucune route n'est écrite ici : chacune est une opération du
 * client GÉNÉRÉ, à travers LA porte de la résidence.
 *
 * ⚠️ Une délégation est en LECTURE SEULE depuis coprofirst#1534 : l'aidant lit ce que lit
 * la personne aidée, sans agir en son nom. L'app n'a donc aucun « agir pour… ».
 */
interface ServiceDelegations {
    /** GET /delegations : celles que le compte voit (le conseil : toutes ; sinon les siennes). */
    suspend fun delegations(): List<Delegation>

    /** GET /admin/utilisateurs : les comptes qu'une délégation peut lier (conseil syndical). */
    suspend fun residentsDelegables(): List<ResidentDelegable>

    /** POST /delegations (conseil syndical) : la délégation créée, en attente de l'aidant. */
    suspend fun creer(saisie: SaisieDelegation): Delegation

    /** POST /delegations/{id}/accepter (l'aidant) : la délégation devenue active. */
    suspend fun accepter(id: Int): Delegation

    /** POST /delegations/{id}/revoquer (la personne aidée, l'aidant ou le conseil). */
    suspend fun revoquer(id: Int): Delegation
}

/** Les Délégations par le client généré (`DelegationRead`, `UtilisateurAdminRead`). */
class ServiceDelegationsApi(private val session: SessionOuverte) : ServiceDelegations {
    override suspend fun delegations() = session.appeler { b, c -> DlgationsAidantApi(b, c).getDelegations().map { it.lue() } }

    override suspend fun residentsDelegables() =
        session.appeler { b, c -> AdminApi(b, c).getAdminUtilisateurs().map { it.lu() } }

    override suspend fun creer(saisie: SaisieDelegation): Delegation {
        val corps = saisie.corps()
        return session.appeler { b, c -> DlgationsAidantApi(b, c).postDelegations(corps).lue() }
    }

    override suspend fun accepter(id: Int) =
        session.appeler { b, c -> DlgationsAidantApi(b, c).postDelegationsDelegationIdAccepter(id).lue() }

    override suspend fun revoquer(id: Int) =
        session.appeler { b, c -> DlgationsAidantApi(b, c).postDelegationsDelegationIdRevoquer(id).lue() }
}

/** Une délégation, telle que le serveur la sert (`DelegationRead`, coprofirst#2525). */
data class Delegation(
    val id: Int,
    /** « ? » quand le compte n'existe plus — le nom tel que le serveur le compose. */
    val mandantNom: String,
    val aidantNom: String,
    val statut: String,
    val motif: String,
    /** Jours ISO (`AAAA-MM-JJ`) ; `dateFin` absente : sans limite. */
    val dateDebut: String,
    val dateFin: String?,
    /**
     * Les gestes que le serveur ouvre au compte qui lit (#56 de l'iOS) : l'app ne les
     * déduit ni du statut ni de qui est l'aidant. La réponse d'un geste porte ceux d'après.
     */
    val peutAccepter: Boolean = false,
    val peutRevoquer: Boolean = false,
) {
    /** Le libellé et la teinte de l'état ; un état inconnu s'affiche brut, en gris. */
    val libelleStatut: LibelleTeinte get() = TablesDelegations.statuts[statut] ?: LibelleTeinte.brut(statut)
}

internal fun DelegationRead.lue() = Delegation(
    id = id,
    mandantNom = mandantNom,
    aidantNom = aidantNom,
    statut = statut,
    motif = motif,
    dateDebut = dateDebut.toString(),
    dateFin = dateFin?.toString(),
    peutAccepter = peutAccepter ?: false,
    peutRevoquer = peutRevoquer ?: false,
)

/** Un compte qu'une délégation peut lier (`UtilisateurAdminRead`, champs lus). */
data class ResidentDelegable(val id: Int, val nomAffiche: String, val email: String, val actif: Boolean) {
    /** « Lucie EXEMPLE (lucie@exemple.test) », comme le menu du site. */
    val choix: String get() = "$nomAffiche ($email)"
}

internal fun UtilisateurAdminRead.lu() =
    ResidentDelegable(id, FormatsDuSite.nomServi(nomAffiche), email, actif)

/** La saisie de « Nouvelle délégation » (`DelegationCreate`). */
data class SaisieDelegation(
    val mandant: Int? = null,
    val aidant: Int? = null,
    val motif: String = "",
    /** Sans date, la délégation reste valable sans limite. */
    val dateFin: LocalDate? = null,
) {
    /** La règle du formulaire du site : deux personnes, distinctes. */
    val complete: Boolean get() = mandant != null && aidant != null && mandant != aidant

    /** Changer de personne aidée retire l'aidant s'il devient la même personne. */
    fun avecMandant(id: Int?) = copy(mandant = id, aidant = aidant.takeIf { it != id })

    internal fun corps(): DelegationCreate {
        val m = requireNotNull(mandant) { "la personne aidée est exigée (l'écran le vérifie)" }
        val a = requireNotNull(aidant) { "l'aidant est exigé (l'écran le vérifie)" }
        // Le jour seul, en ISO (`AAAA-MM-JJ`) : celui que le compte a choisi, sans fuseau.
        return DelegationCreate(m, a, motif.trim(), dateFin?.toString())
    }
}

/** Seuls les comptes actifs peuvent être liés (le formulaire du site). */
fun List<ResidentDelegable>.liables(): List<ResidentDelegable> = filter { it.actif }
