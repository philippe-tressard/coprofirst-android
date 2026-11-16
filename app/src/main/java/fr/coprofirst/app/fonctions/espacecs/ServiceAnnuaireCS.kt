// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.espacecs

import fr.coprofirst.api.modeles.CompositionCSIn
import fr.coprofirst.api.modeles.MembreCSIn
import fr.coprofirst.api.modeles.MembreConseilAdmin
import fr.coprofirst.api.modeles.MembreSyndicAdmin
import fr.coprofirst.api.modeles.MembreSyndicIn
import fr.coprofirst.api.modeles.SyndicIn
import fr.coprofirst.api.routes.AdminApi
import fr.coprofirst.app.commun.annuaire.Annuaire
import fr.coprofirst.app.commun.formats.FormatsDuSite
import fr.coprofirst.app.session.SessionOuverte

/**
 * Ce que l'onglet « Annuaire CS & Syndic » demande, comme l'iOS (`ServiceAnnuaireCS`) : la
 * composition du conseil et la fiche du syndic, à LIRE et à RÉÉCRIRE. Chaque enregistrement
 * envoie la liste ENTIÈRE (en-tête et membres), comme le site, et rend la composition
 * ENREGISTRÉE — ce que l'écran montre ensuite.
 */
interface ServiceAnnuaireCS {
    /** GET /admin/annuaire/cs. */
    suspend fun conseil(): ConseilCS

    /** PUT /admin/annuaire/cs : le conseil tel que le serveur l'a retenu. */
    suspend fun enregistrer(conseil: ConseilCS): ConseilCS

    /** GET /admin/annuaire/syndic. */
    suspend fun syndic(): SyndicCS

    /** PUT /admin/annuaire/syndic : le syndic tel que le serveur l'a retenu. */
    suspend fun enregistrer(syndic: SyndicCS): SyndicCS

    /**
     * GET /admin/annuaire/rapprochement : ce qu'un nom saisi désigne — l'inscrit de même nom
     * et, s'il faut le `localiser` (conseil), son logement. La règle est au serveur : le PUT
     * l'applique de toute façon, l'écran la DEMANDE pour la montrer à la frappe.
     */
    suspend fun rapprochement(nom: String, localiser: Boolean): Rapprochement
}

class ServiceAnnuaireCSApi(private val session: SessionOuverte) : ServiceAnnuaireCS {
    override suspend fun conseil() = session.appeler { b, c ->
        AdminApi(b, c).getAdminAnnuaireCs().let { ConseilCS(it.agAnnee, it.agDate, it.whatsappUrl.nonVide(), it.membres.map { m -> m.lu() }) }
    }

    override suspend fun enregistrer(conseil: ConseilCS) = session.appeler { b, c ->
        val corps = CompositionCSIn(conseil.agAnnee, conseil.agDate.nonVide(), conseil.whatsappUrl.nonVide(), conseil.membres.map { it.entree() })
        AdminApi(b, c).putAdminAnnuaireCs(corps).let { ConseilCS(it.agAnnee, it.agDate, it.whatsappUrl.nonVide(), it.membres.map { m -> m.lu() }) }
    }

    override suspend fun syndic() = session.appeler { b, c ->
        AdminApi(b, c).getAdminAnnuaireSyndic().let {
            SyndicCS(it.nomSyndic.nonVide(), it.nomSyndicSource.value, it.adresse.nonVide(), it.siteWeb, it.membres.map { m -> m.lu() })
        }
    }

    override suspend fun enregistrer(syndic: SyndicCS) = session.appeler { b, c ->
        val corps = SyndicIn(syndic.nomSyndic.orEmpty(), syndic.adresse.orEmpty(), syndic.siteWeb.nonVide(), syndic.membres.map { it.entree() })
        AdminApi(b, c).putAdminAnnuaireSyndic(corps).let {
            SyndicCS(it.nomSyndic.nonVide(), it.nomSyndicSource.value, it.adresse.nonVide(), it.siteWeb, it.membres.map { m -> m.lu() })
        }
    }

    override suspend fun rapprochement(nom: String, localiser: Boolean) = session.appeler { b, c ->
        AdminApi(b, c).getAdminAnnuaireRapprochement(nom, localiser).let {
            Rapprochement(it.userId, it.batimentId, it.batimentNom, it.etage, it.etageLibelle.nonVide())
        }
    }
}

/** Le conseil syndical : l'AG qui l'a élu (« 2026-06-12 », tel que servi), la communauté WhatsApp, ses membres. */
data class ConseilCS(
    val agAnnee: Int? = null,
    val agDate: String? = null,
    val whatsappUrl: String? = null,
    val membres: List<MembreConseil> = emptyList(),
) {
    /** Désigne le président — un seul : le choix en retire l'ancien (`surPresident`). */
    fun avecPresident(rang: Int?) = copy(membres = membres.mapIndexed { i, m -> m.copy(estPresident = i == rang) })
}

/** Un membre du conseil. `id` est nul tant que le serveur ne l'a pas enregistré. */
data class MembreConseil(
    val id: Int? = null,
    val genre: String? = null,
    val prenom: String? = null,
    val nom: String? = null,
    val batimentId: Int? = null,
    val batimentNom: String? = null,
    val etage: Int? = null,
    /** L'étage tel que le serveur le dit (`etage_libelle`) — l'app ne le compose pas. */
    val etageLibelle: String? = null,
    val estGestionnaireSite: Boolean = false,
    val estPresident: Boolean = false,
    val photoUrl: String? = null,
    /** L'inscrit auquel la fiche est liée — conservé tel quel à chaque envoi. */
    val userId: Int? = null,
    /** « Prénom NOM » servi ; `null` pour une fiche neuve. */
    val nomServi: String? = null,
) {
    val nomAffiche: String get() = FormatsDuSite.nom(nomServi, prenom, nom)

    /** La fiche telle que l'annuaire la rend (`CarteContact`). */
    val enCarte: Annuaire.Membre
        get() = Annuaire.Membre(
            id ?: 0, genre, prenom, nom, nomServi, batimentNom, estGestionnaireSite, estPresident, photoUrl,
        )
}

/**
 * Le syndic : son nom (le contrat de syndic fait foi s'il en désigne un : `nomSyndicSource`
 * = `contrat`), son adresse, son espace client, ses interlocuteurs dans l'ordre choisi.
 */
data class SyndicCS(
    val nomSyndic: String? = null,
    val nomSyndicSource: String? = null,
    val adresse: String? = null,
    val siteWeb: String? = null,
    val membres: List<MembreSyndic> = emptyList(),
) {
    /** Le nom vient du contrat de syndic : il ne se saisit pas ici (`utils/syndic.py`). */
    val nomDuContrat: Boolean get() = nomSyndicSource == "contrat"

    /** Désigne l'interlocuteur principal — un seul (`designer`). */
    fun avecPrincipal(rang: Int?) = copy(membres = membres.mapIndexed { i, m -> m.copy(estPrincipal = i == rang) })

    /** Monte (`sens` = -1) ou descend (+1) un interlocuteur : l'ordre est celui de l'annuaire. */
    fun deplace(rang: Int, sens: Int): SyndicCS {
        val cible = rang + sens
        if (rang !in membres.indices || cible !in membres.indices) return this
        val l = membres.toMutableList()
        l[rang] = membres[cible].also { l[cible] = membres[rang] }
        return copy(membres = l)
    }
}

/** Un interlocuteur du syndic. */
data class MembreSyndic(
    val id: Int? = null,
    val genre: String? = null,
    val prenom: String? = null,
    val nom: String? = null,
    val fonction: String? = null,
    val email: String? = null,
    /** Les numéros tels que le serveur les SERT ; il en REÇOIT un seul champ, séparé par des virgules. */
    val telephones: List<String> = emptyList(),
    val estPrincipal: Boolean = false,
    val photoUrl: String? = null,
    val userId: Int? = null,
    val nomServi: String? = null,
) {
    val nomAffiche: String get() = FormatsDuSite.nom(nomServi, prenom, nom)

    val enCarte: Annuaire.Membre
        get() = Annuaire.Membre(
            id ?: 0, genre, prenom, nom, nomServi, photoUrl = photoUrl, fonction = fonction, email = email,
            telephones = telephones, estPrincipal = estPrincipal,
        )
}

/** Ce qu'un nom saisi désigne : l'inscrit actif de même nom et, pour le conseil, son logement. */
data class Rapprochement(
    val userId: Int? = null,
    val batimentId: Int? = null,
    /** Le NUMÉRO du bâtiment (« A »). */
    val batimentNom: String? = null,
    val etage: Int? = null,
    /** L'étage en clair, servi ; `null` sans logement. */
    val etageLibelle: String? = null,
)

/** Ce que l'annuaire du conseil partage entre ses écrans. */
object AnnuaireCS {
    /** La civilité d'une fiche neuve : la première du site (`CIVILITES`, générée). */
    val civiliteParDefaut: String get() = TablesEspaceCS.civilites.firstOrNull().orEmpty()

    /** Les champs de saisie des numéros : ceux que le serveur sert, un champ vide au moins. */
    fun champsTelephone(servis: List<String>): List<String> = servis.ifEmpty { listOf("") }

    /** …et rendus à la forme que le serveur reçoit. */
    fun champTelephone(numeros: List<String>): String? = numeros.map { it.trim() }.filter { it.isNotEmpty() }.joinToString(",").nonVide()

    /** Une fiche se nomme : un prénom ou un nom, au moins. */
    fun nomme(prenom: String?, nom: String?): Boolean = !prenom.isNullOrBlank() || !nom.isNullOrBlank()
}

private fun String?.nonVide(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

internal fun MembreConseilAdmin.lu() = MembreConseil(
    id, genre.nonVide(), prenom, nom, batimentId, batimentNom.nonVide(), etage, etageLibelle.nonVide(), estGestionnaireSite,
    estPresident, photoUrl.nonVide(), userId, nomAffiche,
)

internal fun MembreSyndicAdmin.lu() = MembreSyndic(
    id, genre.nonVide(), prenom, nom, fonction.nonVide(), email.nonVide(), telephones.mapNotNull { it.nonVide() }, estPrincipal,
    photoUrl.nonVide(), userId, nomAffiche,
)

private fun MembreConseil.entree() = MembreCSIn(
    genre ?: AnnuaireCS.civiliteParDefaut, prenom.orEmpty(), nom.orEmpty(), id, batimentId, etage, estPresident, userId,
)

private fun MembreSyndic.entree() = MembreSyndicIn(
    genre ?: AnnuaireCS.civiliteParDefaut, prenom.orEmpty(), nom.orEmpty(), id, fonction.nonVide(), email.nonVide(),
    AnnuaireCS.champTelephone(telephones), estPrincipal, userId,
)
