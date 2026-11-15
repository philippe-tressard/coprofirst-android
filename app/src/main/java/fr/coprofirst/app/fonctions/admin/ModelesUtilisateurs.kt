// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.admin

import fr.coprofirst.api.modeles.DemandeProfilEnAttente
import fr.coprofirst.api.modeles.GestesRoles
import fr.coprofirst.api.modeles.UserRead
import fr.coprofirst.api.modeles.UtilisateurAdminRead
import fr.coprofirst.app.commun.formats.FormatsDuSite
import java.text.Collator
import java.time.Instant
import java.util.Locale

/** Un compte, tel que l'administration le lit (`UtilisateurAdminRead`). */
data class UtilisateurAdmin(
    val id: Int,
    val prenom: String,
    val nom: String,
    val email: String,
    val statut: String,
    /** Les rôles portés ; `role` seul si la liste est vide (`aRole` du site). */
    val roles: List<String>,
    val actif: Boolean,
    val emailVerifie: Boolean,
    val batimentNom: String? = null,
    /** Le bailleur d'un locataire. */
    val nomProprietaire: String? = null,
    val aDesLots: Boolean = false,
    /** Loti, TC, Vigik, Bail : `ok`, `manque` ou `sans_objet`, par le serveur. */
    val etiquettes: Map<String, String> = emptyMap(),
    val ban: BanCommunaute = BanCommunaute(),
    /** Les gestes de rôle SERVIS (`gestes_roles`) : l'app ne recompose pas la règle. */
    val gestesRoles: List<GesteDeRole> = emptyList(),
    /** Averti de sa suppression pour inactivité ; s'efface à la reconnexion. */
    val purgeAvertieLe: Instant? = null,
    val derniereConnexion: Instant? = null,
    val creeLe: Instant? = null,
    /** `nom_affiche` servi. */
    val nomServi: String? = null,
) {
    /** « Prénom NOM » servi ; à défaut, le repli de `FormatsDuSite.nom`. */
    val nomAffiche: String get() = FormatsDuSite.nom(nomServi, prenom, nom)

    /**
     * « Opérateur désigné par l'exploitant » (`GestesRolesCompte.svelte`) : ce que l'écran
     * DIT, pas ce qu'il offre — sur une instance commune, un compte actif qui porte le rôle
     * et dont le serveur n'offre aucun geste d'opérateur.
     */
    fun operateurDesigneParLExploitant(instanceCommune: Boolean): Boolean =
        instanceCommune && actif && ROLE_OPERATEUR in roles && gestesRoles.none { it.role == ROLE_OPERATEUR }

    companion object {
        const val ROLE_ADMIN = "admin"
        const val ROLE_OPERATEUR = "operateur"
        const val ROLE_CONSEIL = "conseil_syndical"
        const val STATUT_LOCATAIRE = "locataire"
    }
}

/** Ce que le serveur dit de l'accès d'un compte à la communauté. */
data class BanCommunaute(
    val interdit: Boolean = false,
    val jusquAu: Instant? = null,
    /** `communaute_banni` : exclu À CET INSTANT, jugé à l'heure du serveur. */
    val banni: Boolean = false,
)

/** Un geste de rôle, tel que la route le nomme. */
enum class GesteRole { Ajouter, Retirer }

/** Un geste de rôle offert sur un compte (`gestes_roles` du serveur) : le rôle et ce qu'on peut en faire. */
data class GesteDeRole(val role: String, val geste: GesteRole)

/** Ce que la « Fiche » d'un compte confirme avant un geste de rôle, comme l'iOS (`ConfirmationRole`). */
data class ConfirmationRole(
    /** Sur le compte du lecteur : la question dit « votre compte ». */
    val pourSoi: Boolean,
    val suite: Suite?,
) {
    /** Ce qui suit la question : la notification d'un autre compte, ou ce que le lecteur perd. */
    enum class Suite { Notifie, PerdraLAdministration, GarderaLaSupervision }

    companion object {
        /** `lecteur` : l'identifiant du compte qui lit (GET /auth/me) ; inconnu, il n'est titulaire d'aucun compte. */
        fun de(offert: GesteDeRole, compte: UtilisateurAdmin, lecteur: Int?): ConfirmationRole {
            val pourSoi = lecteur == compte.id
            val suite = when {
                !pourSoi -> Suite.Notifie
                offert.geste == GesteRole.Retirer && offert.role == UtilisateurAdmin.ROLE_ADMIN ->
                    if (UtilisateurAdmin.ROLE_OPERATEUR in compte.roles) Suite.GarderaLaSupervision else Suite.PerdraLAdministration
                else -> null
            }
            return ConfirmationRole(pourSoi, suite)
        }
    }
}

/** Le filtre « compte » de la liste : tous, actifs, en attente. */
enum class FiltreCompte { Tous, Actifs, EnAttente }

/** Les règles de la liste « Utilisateurs », celles du site (`FiltresUtilisateurs.svelte`, `comparerParNom`). */
object ListeUtilisateurs {
    private val francais: Collator = Collator.getInstance(Locale.FRENCH).apply { strength = Collator.PRIMARY }

    /** Les comptes retenus par la recherche, le type et le filtre, triés par nom puis prénom. */
    fun affiches(comptes: List<UtilisateurAdmin>, recherche: String, statut: String?, filtre: FiltreCompte): List<UtilisateurAdmin> =
        comptes.filter { u ->
            (statut == null || u.statut == statut) &&
                (filtre == FiltreCompte.Tous || (filtre == FiltreCompte.Actifs) == u.actif) &&
                fr.coprofirst.app.commun.texte.Recherche.contientTousLesMots(listOf(u.prenom, u.nom, u.email), recherche)
        }.sortedWith(parNom)

    /** Par nom, puis prénom, sans casse ni accents ; un compte sans nom en dernier. */
    val parNom: Comparator<UtilisateurAdmin> = Comparator { a, b ->
        val (nomA, nomB) = a.nom.trim() to b.nom.trim()
        when {
            nomA.isEmpty() != nomB.isEmpty() -> if (nomA.isEmpty()) 1 else -1
            else -> francais.compare(nomA, nomB).takeIf { it != 0 } ?: francais.compare(a.prenom, b.prenom)
        }
    }

    /** Les membres du conseil syndical, sur toute la liste. */
    fun membresDuConseil(comptes: List<UtilisateurAdmin>): Int = comptes.count { UtilisateurAdmin.ROLE_CONSEIL in it.roles }
}

/** L'état d'un compte en pastille : actif ; sinon, faute d'e-mail vérifié ; sinon, en attente de validation. */
enum class EtatCompte(val badge: String) { Actif("badge-green"), EmailNonVerifie("badge-orange"), EnAttente("badge-gray") }

val UtilisateurAdmin.etat: EtatCompte
    get() = when {
        actif -> EtatCompte.Actif
        !emailVerifie -> EtatCompte.EmailNonVerifie
        else -> EtatCompte.EnAttente
    }

internal fun UtilisateurAdminRead.lu(): UtilisateurAdmin {
    val portes = roles.orEmpty()
    return UtilisateurAdmin(
        id = id,
        prenom = prenom,
        nom = nom,
        email = email,
        statut = statut.value,
        roles = portes.ifEmpty { listOf(role.value) },
        actif = actif,
        emailVerifie = emailVerifie ?: false,
        batimentNom = batimentNom,
        nomProprietaire = nomProprietaire,
        aDesLots = hasLots,
        etiquettes = mapOf("loti" to etiquettes.loti, "tc" to etiquettes.tc, "vigik" to etiquettes.vigik, "bail" to etiquettes.bail),
        ban = BanCommunaute(communauteInterdit ?: false, communauteBanJusquAu?.toInstant(), communauteBanni ?: false),
        gestesRoles = gestesRoles.servis(),
        purgeAvertieLe = purgeAvertieLe?.toInstant(),
        derniereConnexion = derniereConnexion?.toInstant(),
        creeLe = creeLe.toInstant(),
        nomServi = nomAffiche,
    )
}

/** L'état que rend un geste sur la communauté (`UserRead`). */
internal fun UserRead.ban() = BanCommunaute(communauteInterdit ?: false, communauteBanJusquAu?.toInstant(), communauteBanni ?: false)

/** Les gestes servis, dans l'ordre du site (`GestesRolesCompte.svelte`) ; un rôle sans geste n'offre rien. */
internal fun GestesRoles.servis(): List<GesteDeRole> = listOfNotNull(
    conseilSyndical?.value?.let { UtilisateurAdmin.ROLE_CONSEIL to it },
    admin?.value?.let { UtilisateurAdmin.ROLE_ADMIN to it },
    operateur?.value?.let { UtilisateurAdmin.ROLE_OPERATEUR to it },
).mapNotNull { (role, geste) ->
    when (geste) {
        "ajouter" -> GesteDeRole(role, GesteRole.Ajouter)
        "retirer" -> GesteDeRole(role, GesteRole.Retirer)
        else -> null
    }
}

/** Une demande de modification de profil en attente, et ce que la liste y ajoute (le demandeur, son statut et son bâtiment actuels). */
data class DemandeProfil(
    val id: Int,
    val utilisateurNom: String,
    val utilisateurEmail: String? = null,
    val statutActuel: String? = null,
    val batimentActuel: String? = null,
    val statutSouhaite: String? = null,
    val batimentNomSouhaite: String? = null,
    val motif: String? = null,
    val creeLe: Instant? = null,
)

internal fun DemandeProfilEnAttente.lu() = DemandeProfil(
    id = id,
    utilisateurNom = utilisateurNom,
    utilisateurEmail = utilisateurEmail,
    statutActuel = statutActuel,
    batimentActuel = batimentActuel,
    statutSouhaite = statutSouhaite,
    batimentNomSouhaite = batimentNomSouhaite,
    motif = motif,
    creeLe = creeLe.toInstant(),
)
