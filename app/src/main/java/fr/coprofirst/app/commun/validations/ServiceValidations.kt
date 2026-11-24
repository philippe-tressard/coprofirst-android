// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.validations

import fr.coprofirst.api.modeles.AccueilArrivantBody
import fr.coprofirst.api.modeles.CommandeAccesEnAttente
import fr.coprofirst.api.modeles.CommandeAction
import fr.coprofirst.api.modeles.CompteAction
import fr.coprofirst.api.modeles.UserRead
import fr.coprofirst.api.routes.AdminApi
import fr.coprofirst.app.commun.PhraseAvis
import fr.coprofirst.app.commun.TonAvis
import fr.coprofirst.app.commun.formats.FormatsDuSite
import fr.coprofirst.app.session.SessionOuverte
import java.time.Instant

/**
 * La FILE DE TRAVAIL du conseil (onglet `validations`, « Comptes & accès ») : les comptes à
 * valider, les demandes de badges et de télécommandes — comme l'iOS (`ServiceComptesCS`,
 * `Commun/Validations`). Les routes sont celles du conseil ET de l'administration
 * (`require_cs_or_admin`) : « À traiter » du Paramétrage (#26) ouvre la même file.
 * Le serveur refuse chaque route à qui n'est ni l'un ni l'autre.
 */
interface ServiceValidations {
    /** GET /admin/comptes-en-attente. */
    suspend fun comptesEnAttente(): List<CompteEnAttente>

    /** GET /admin/commandes-acces. */
    suspend fun commandesEnAttente(): List<CommandeEnAttente>

    /**
     * POST /admin/comptes/{id}/traiter — `valider` ou `refuser`. Rend l'`annonce` du
     * serveur, avec son ton (« ⚠️ Aucun lot trouvé dans l'import »…) ; aucune sur un refus.
     */
    suspend fun traiterCompte(id: Int, valider: Boolean): PhraseAvis?

    /** POST /admin/utilisateurs/{id}/accueil-arrivant : l'accueil d'un résident qui emménage. */
    suspend fun accueillir(id: Int, accueil: AccueilArrivant)

    /**
     * POST /admin/commandes-acces/{id}/traiter — accepter (avec les codes remis, s'il y en
     * a : le serveur les pose sur le lot commandé ; `codes` vide sinon) ou refuser (`null`).
     */
    suspend fun traiterCommande(id: Int, codes: List<String>?)
}

/** La file par le client GÉNÉRÉ, à travers LA porte de la résidence. */
class ServiceValidationsApi(private val session: SessionOuverte) : ServiceValidations {
    override suspend fun comptesEnAttente() =
        session.appeler { b, c -> AdminApi(b, c).getAdminComptesEnAttente().map { it.lu() } }

    override suspend fun commandesEnAttente() =
        session.appeler { b, c -> AdminApi(b, c).getAdminCommandesAcces().map { it.lu() } }

    override suspend fun traiterCompte(id: Int, valider: Boolean) = session.appeler { b, c ->
        val action = CompteAction(if (valider) "valider" else "refuser")
        AdminApi(b, c).postAdminComptesUserIdTraiter(id, action).annonce?.let { PhraseAvis(it.texte, TonAvis.depuis(it.ton.value)) }
    }

    override suspend fun accueillir(id: Int, accueil: AccueilArrivant) {
        val corps = AccueilArrivantBody(
            batiment = accueil.batiment.trim().ifEmpty { null },
            ancienResident = accueil.ancienResident.trim().ifEmpty { null },
        )
        session.appeler { b, c -> AdminApi(b, c).postAdminUtilisateursUserIdAccueilArrivant(id, corps) }
    }

    override suspend fun traiterCommande(id: Int, codes: List<String>?) {
        val action = CommandeAction(if (codes == null) "refuser" else "accepter", codes = codes.orEmpty())
        session.appeler { b, c -> AdminApi(b, c).postAdminCommandesAccesCmdIdTraiter(id, action) }
    }
}

/** Un compte en attente de validation (`UserRead`, ce que la carte en montre). */
data class CompteEnAttente(
    val id: Int,
    val statut: String,
    /** Le bâtiment tel que le serveur le nomme. */
    val batimentNom: String? = null,
    /** L'aidé, pour un compte d'aidant ou de mandataire : sa présence décide qu'on le nomme. */
    val prenomAide: String? = null,
    val nomAide: String? = null,
    val creeLe: Instant? = null,
    /** `nom_affiche` et `nom_aide_affiche` servis : jamais composés ici. */
    val nomServi: String? = null,
    val nomAideServi: String? = null,
) {
    val nomAffiche: String get() = FormatsDuSite.nomServi(nomServi)

    /** La personne aidée, nommée de même — `null` sans elle. */
    val nomAideAffiche: String?
        get() = if (prenomAide == null && nomAide == null) null else FormatsDuSite.nomServi(nomAideServi)
}

/** Une demande de badge ou de télécommande en attente (`CommandeAccesEnAttente`). */
data class CommandeEnAttente(
    val id: Int,
    val type: String,
    val quantite: Int,
    val demandeurNom: String,
    /** Le lot, tel que le serveur le libelle. */
    val lot: String,
    val batiment: String? = null,
    val creeLe: Instant? = null,
)

/** Ce que la case « Nouvel Arrivant » envoie (`AccueilArrivantBody`). */
data class AccueilArrivant(val batiment: String = "", val ancienResident: String = "")

/**
 * La saisie des codes remis, découpée comme le site (`accepterCommandeAcces`) : virgules,
 * points-virgules ou espaces.
 */
fun codesSaisis(saisie: String): List<String> = saisie.split(',', ';', ' ', '\t', '\n').filter { it.isNotBlank() }

/** Un compte de la file servie — le MÊME branchement pour le service réel et les tests. */
internal fun UserRead.lu() = CompteEnAttente(
    id, statut.value, batimentNom, prenomAide, nomAide, creeLe.toInstant(), nomAffiche, nomAideAffiche,
)

internal fun CommandeAccesEnAttente.lu() = CommandeEnAttente(id, type, quantite, demandeurNom, lot, batiment, creeLe.toInstant())
