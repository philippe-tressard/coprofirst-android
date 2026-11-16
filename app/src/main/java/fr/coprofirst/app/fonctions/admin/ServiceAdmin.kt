// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.admin

import fr.coprofirst.api.modeles.BanCommunauteBody
import fr.coprofirst.api.modeles.DemandeProfilAction
import fr.coprofirst.api.modeles.RoleAction
import fr.coprofirst.api.routes.AdminApi
import fr.coprofirst.api.routes.AuthApi
import fr.coprofirst.api.routes.ConfigApi
import fr.coprofirst.api.routes.TelemetryApi
import fr.coprofirst.app.commun.PhraseAvis
import fr.coprofirst.app.commun.TonAvis
import fr.coprofirst.app.session.ErreurService
import fr.coprofirst.app.session.SessionOuverte

/**
 * Ce que le Paramétrage (#26) demande à la résidence, comme l'iOS (`ServiceMaintenance`,
 * `ServiceTelemetrie`…). Aucune route n'est écrite ici : chacune est une opération du
 * client GÉNÉRÉ. Chaque lecture n'est offerte qu'au rôle que sa route accepte (l'onglet
 * le juge sur les droits du compte) ; le serveur reste le seul juge, et un refus
 * s'affiche avec sa phrase (`ErreurService.Refus`).
 *
 * Ce que l'app ne porte pas — lancer une tâche, un export, les autres onglets — reste au
 * site, et l'écran le dit (`RenvoiAuSite`).
 */
interface ServiceAdmin {
    /** GET /admin/maintenance/sante (opérateur de la plateforme) : la santé des tâches planifiées. */
    suspend fun santeTaches(): SanteTaches

    /** GET /admin/export-copropriete/dernier (administration) : le dernier export de la copropriété. */
    suspend fun dernierExport(): DernierExport

    /** GET /telemetry/dashboard (administration) : le tableau d'une vue, avec ou sans le gestionnaire du site. */
    suspend fun tableauTelemetrie(portee: PorteeTelemetrie, sansGestionnaire: Boolean): TableauTelemetrie

    /** GET /admin/utilisateurs (conseil et administration) : tous les comptes, avec les gestes de rôle servis. */
    suspend fun utilisateurs(): List<UtilisateurAdmin>

    /**
     * Le compte qui lit (GET /auth/me) et si l'instance est commune (`operateur_par_exploitant`
     * de GET /config, clé calculée par le serveur) — ce que la fiche DIT, aucun geste n'en dépend.
     */
    suspend fun lecteur(): Lecteur

    /** POST /admin/utilisateurs/{id}/ajouter-role | retirer-role (administration) — un geste SERVI. */
    suspend fun changerRole(id: Int, geste: GesteDeRole)

    /** PATCH /admin/utilisateurs/{id}/ban-communaute (administration) — rend l'état servi. */
    suspend fun bannirCommunaute(id: Int, interdit: Boolean): BanCommunaute

    /** POST /admin/utilisateurs/{id}/auto-match (conseil et administration) — l'annonce du serveur, avec son ton. */
    suspend fun relancerRapprochement(id: Int): PhraseAvis

    /** DELETE /admin/utilisateurs/{id} (administration) — définitif. */
    suspend fun supprimerUtilisateur(id: Int)

    /** GET /admin/demandes-profil (conseil et administration) : les changements de statut ou de bâtiment demandés. */
    suspend fun demandesProfil(): List<DemandeProfil>

    /** POST /admin/demandes-profil/{id}/traiter — approuver, ou rejeter avec un motif ; rend l'annonce SERVIE. */
    suspend fun traiterDemandeProfil(id: Int, approuver: Boolean, motif: String?): PhraseAvis
}

/** Le compte qui lit la liste, et si l'instance est commune (#94 de l'iOS). */
data class Lecteur(val id: Int?, val instanceCommune: Boolean)

/** Le Paramétrage par le client généré, à travers LA porte de la résidence. */
class ServiceAdminApi(private val session: SessionOuverte) : ServiceAdmin {
    override suspend fun santeTaches() = session.appeler { b, c -> AdminApi(b, c).getAdminMaintenanceSante().lu() }

    override suspend fun dernierExport() = session.appeler { b, c -> AdminApi(b, c).getAdminExportCoproprieteDernier().lu() }

    override suspend fun tableauTelemetrie(portee: PorteeTelemetrie, sansGestionnaire: Boolean) = session.appeler { b, c ->
        TelemetryApi(b, c).getTelemetryDashboard(portee.code, if (sansGestionnaire) SANS else AVEC).lu()
    }

    override suspend fun utilisateurs() = session.appeler { b, c -> AdminApi(b, c).getAdminUtilisateurs().map { it.lu() } }

    override suspend fun lecteur(): Lecteur {
        // Ni l'un ni l'autre ne décide d'un geste : illisibles, la fiche dit la notification et rien de l'exploitant.
        val id = try {
            session.appeler { b, c -> AuthApi(b, c).getAuthMe().id }
        } catch (_: ErreurService) {
            null
        }
        val commune = try {
            session.appeler { b, c -> ConfigApi(b, c).getConfig()[CLE_EXPLOITANT] } == "1"
        } catch (_: ErreurService) {
            false
        }
        return Lecteur(id, commune)
    }

    override suspend fun changerRole(id: Int, geste: GesteDeRole) {
        session.appeler { b, c ->
            val api = AdminApi(b, c)
            when (geste.geste) {
                GesteRole.Ajouter -> api.postAdminUtilisateursUserIdAjouterRole(id, RoleAction(geste.role))
                GesteRole.Retirer -> api.postAdminUtilisateursUserIdRetirerRole(id, RoleAction(geste.role))
            }
        }
    }

    override suspend fun bannirCommunaute(id: Int, interdit: Boolean) = session.appeler { b, c ->
        AdminApi(b, c).patchAdminUtilisateursUserIdBanCommunaute(id, BanCommunauteBody(interdit)).ban()
    }

    override suspend fun relancerRapprochement(id: Int) = session.appeler { b, c ->
        AdminApi(b, c).postAdminUtilisateursUserIdAutoMatch(id).annonce.let { PhraseAvis(it.texte, TonAvis.depuis(it.ton.value)) }
    }

    override suspend fun supprimerUtilisateur(id: Int) {
        session.appeler { b, c -> AdminApi(b, c).deleteAdminUtilisateursUserId(id) }
    }

    override suspend fun demandesProfil() = session.appeler { b, c -> AdminApi(b, c).getAdminDemandesProfil().map { it.lu() } }

    override suspend fun traiterDemandeProfil(id: Int, approuver: Boolean, motif: String?) = session.appeler { b, c ->
        val action = DemandeProfilAction(
            action = if (approuver) "approuver" else "rejeter",
            motifRefus = motif?.trim()?.ifEmpty { null }.takeUnless { approuver },
        )
        AdminApi(b, c).postAdminDemandesProfilDemandeIdTraiter(id, action).annonce.let { PhraseAvis(it.texte, TonAvis.depuis(it.ton.value)) }
    }

    private companion object {
        /** `1` sur une instance commune, où l'exploitant désigne l'opérateur (`utils/config_publique`). */
        const val CLE_EXPLOITANT = "operateur_par_exploitant"

        /** Le filtre « gestionnaire » de la route : écarté (`sans`) ou compté (`avec`). */
        const val SANS = "sans"
        const val AVEC = "avec"
    }
}
