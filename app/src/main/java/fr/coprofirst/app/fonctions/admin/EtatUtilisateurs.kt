// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.admin

import androidx.annotation.StringRes
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import fr.coprofirst.app.R
import fr.coprofirst.app.commun.PhraseAvis
import fr.coprofirst.app.commun.Roles
import fr.coprofirst.app.session.ErreurService

/**
 * L'état de « Utilisateurs », comme l'iOS (`EtatUtilisateursAdmin`) : la liste chargée,
 * ses filtres (recherche, type, compte) et les gestes. Un geste réussi change la liste
 * comme le serveur l'a changée ; un geste de rôle la RELIT (les gestes d'un compte
 * dépendent des autres : un rôle ne se retire jamais à son dernier titulaire).
 */
@Stable
class EtatUtilisateurs(private val service: ServiceAdmin) {
    var comptes by mutableStateOf<List<UtilisateurAdmin>?>(null)
        private set
    var erreur by mutableStateOf<ErreurService?>(null)
        private set
    var lecteur by mutableStateOf(Lecteur(null, instanceCommune = false))
        private set

    /** Le compte dont un geste est en cours : sa fiche ne se touche plus. */
    var enCours by mutableStateOf<Int?>(null)
        private set
    var recherche by mutableStateOf("")

    /** Un statut de compte (dans l'ordre du site), ou tous. */
    var statut by mutableStateOf<String?>(null)
    var filtre by mutableStateOf(FiltreCompte.Tous)
    private var lecteurLu = false

    val affiches: List<UtilisateurAdmin> get() = ListeUtilisateurs.affiches(comptes.orEmpty(), recherche, statut, filtre)

    fun compte(id: Int): UtilisateurAdmin? = comptes?.firstOrNull { it.id == id }

    /** Une relecture qui échoue garde la liste lue ; la première dit son erreur. */
    suspend fun charger() {
        try {
            comptes = service.utilisateurs()
            erreur = null
        } catch (e: ErreurService) {
            if (comptes == null) erreur = e
        }
        if (!lecteurLu) {
            lecteur = service.lecteur()
            lecteurLu = true
        }
    }

    /** Interdire ou réautoriser : l'état SERVI remplace l'ancien ; l'annonce dit lequel. */
    suspend fun basculerCommunaute(compte: UtilisateurAdmin): AnnonceCommunaute = geste(compte) {
        val interdire = !compte.ban.banni
        val ban = service.bannirCommunaute(compte.id, interdire)
        comptes = comptes?.map { if (it.id == compte.id) it.copy(ban = ban) else it }
        when {
            !interdire -> AnnonceCommunaute.Reautorise
            ban.interdit -> AnnonceCommunaute.BanniDefinitivement
            else -> AnnonceCommunaute.BanniUnMois
        }
    }

    /** Un geste de rôle servi, puis la LISTE relue. */
    suspend fun changerRole(compte: UtilisateurAdmin, offert: GesteDeRole) = geste(compte) {
        service.changerRole(compte.id, offert)
        charger()
    }

    suspend fun relancerRapprochement(compte: UtilisateurAdmin): PhraseAvis = geste(compte) {
        service.relancerRapprochement(compte.id).also { charger() }
    }

    suspend fun supprimer(compte: UtilisateurAdmin) = geste(compte) {
        service.supprimerUtilisateur(compte.id)
        comptes = comptes?.filterNot { it.id == compte.id }
    }

    private suspend fun <T> geste(compte: UtilisateurAdmin, faire: suspend () -> T): T {
        enCours = compte.id
        try {
            return faire()
        } finally {
            enCours = null
        }
    }
}

/** Ce que dit l'annonce d'un geste sur la communauté. */
enum class AnnonceCommunaute(@StringRes val texte: Int) {
    Reautorise(R.string.admin_annonce_reautorise),
    BanniDefinitivement(R.string.admin_annonce_banni_definitivement),
    BanniUnMois(R.string.admin_annonce_banni_un_mois),
}

/** Un rattachement d'un compte (`ETIQUETTES_COMPTE`) : son libellé, et ce que dit chacun des états servis. */
data class EtiquetteCompte(
    val cle: String,
    @StringRes val libelle: Int,
    @StringRes val ok: Int,
    @StringRes val manque: Int,
    @StringRes val sansObjet: Int? = null,
) {
    /** `ok`, `manque` ou `sans_objet` ; un état absent se lit `manque`, comme l'iOS. */
    @StringRes
    fun texte(etat: String?): Int? = when (etat ?: MANQUE) {
        "ok" -> ok
        MANQUE -> manque
        else -> sansObjet
    }

    companion object {
        const val MANQUE = "manque"

        /** Dans l'ordre du site. */
        val toutes = listOf(
            EtiquetteCompte("loti", R.string.admin_fiche_loti, R.string.admin_fiche_loti_ok, R.string.admin_fiche_loti_manque),
            EtiquetteCompte(
                "tc", R.string.admin_fiche_tc, R.string.admin_fiche_tc_ok, R.string.admin_fiche_tc_manque, R.string.admin_fiche_tc_sans_objet,
            ),
            EtiquetteCompte(
                "vigik", R.string.admin_fiche_vigik, R.string.admin_fiche_vigik_ok, R.string.admin_fiche_vigik_manque,
                R.string.admin_fiche_vigik_sans_objet,
            ),
            EtiquetteCompte(
                "bail", R.string.admin_fiche_bail, R.string.admin_fiche_bail_ok, R.string.admin_fiche_bail_manque,
                R.string.admin_fiche_bail_sans_objet,
            ),
        )
    }
}

/** Les mots des gestes de rôle, ceux du site (`GestesRolesCompte.svelte`). */
object TextesRoles {
    /** Le nom d'un rôle sur son bouton : « CS » pour le conseil syndical, comme le site, son libellé pour les autres. */
    fun court(role: String): String = if (role == UtilisateurAdmin.ROLE_CONSEIL) "CS" else Roles.role(role).libelle

    /** « + CS », « – Admin » : le tiret demi-cadratin du site. */
    fun bouton(offert: GesteDeRole): String = (if (offert.geste == GesteRole.Ajouter) "+ " else "– ") + court(offert.role)

    /** Le libellé du rôle, en entier (« Ajouter le rôle Administrateur »). */
    fun libelle(role: String): String = Roles.role(role).libelle

    @StringRes
    fun action(offert: GesteDeRole): Int =
        if (offert.geste == GesteRole.Ajouter) R.string.admin_fiche_role_ajouter else R.string.admin_fiche_role_retirer

    @StringRes
    fun question(offert: GesteDeRole): Int =
        if (offert.geste == GesteRole.Ajouter) R.string.admin_fiche_role_question_ajouter else R.string.admin_fiche_role_question_retirer

    @StringRes
    fun annonce(offert: GesteDeRole): Int =
        if (offert.geste == GesteRole.Ajouter) R.string.admin_annonce_role_ajoute else R.string.admin_annonce_role_retire

    @StringRes
    fun suite(suite: ConfirmationRole.Suite): Int = when (suite) {
        ConfirmationRole.Suite.Notifie -> R.string.admin_fiche_notifie
        ConfirmationRole.Suite.PerdraLAdministration -> R.string.admin_fiche_perdra_administration
        ConfirmationRole.Suite.GarderaLaSupervision -> R.string.admin_fiche_gardera_supervision
    }
}
