// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.admin

import fr.coprofirst.api.routes.AdminApi
import fr.coprofirst.api.routes.TelemetryApi
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
}

/** Le Paramétrage par le client généré, à travers LA porte de la résidence. */
class ServiceAdminApi(private val session: SessionOuverte) : ServiceAdmin {
    override suspend fun santeTaches() = session.appeler { b, c -> AdminApi(b, c).getAdminMaintenanceSante().lu() }

    override suspend fun dernierExport() = session.appeler { b, c -> AdminApi(b, c).getAdminExportCoproprieteDernier().lu() }

    override suspend fun tableauTelemetrie(portee: PorteeTelemetrie, sansGestionnaire: Boolean) = session.appeler { b, c ->
        TelemetryApi(b, c).getTelemetryDashboard(portee.code, if (sansGestionnaire) SANS else AVEC).lu()
    }

    private companion object {
        /** Le filtre « gestionnaire » de la route : écarté (`sans`) ou compté (`avec`). */
        const val SANS = "sans"
        const val AVEC = "avec"
    }
}
