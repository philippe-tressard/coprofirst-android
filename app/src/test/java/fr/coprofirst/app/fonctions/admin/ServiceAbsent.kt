// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.admin

/** Un service dont aucune lecture n'est attendue : un test le délègue et ne réalise que ce qu'il éprouve. */
object ServiceAbsent : ServiceAdmin {
    private fun absent(): Nothing = throw UnsupportedOperationException("lecture non attendue par ce test")

    override suspend fun santeTaches() = absent()
    override suspend fun dernierExport() = absent()
    override suspend fun tableauTelemetrie(portee: PorteeTelemetrie, sansGestionnaire: Boolean) = absent()
    override suspend fun utilisateurs() = absent()
    override suspend fun lecteur() = absent()
    override suspend fun changerRole(id: Int, geste: GesteDeRole) = absent()
    override suspend fun bannirCommunaute(id: Int, interdit: Boolean) = absent()
    override suspend fun relancerRapprochement(id: Int) = absent()
    override suspend fun supprimerUtilisateur(id: Int) = absent()
}
