// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.annuaire

import fr.coprofirst.api.routes.AdminApi
import fr.coprofirst.app.commun.annuaire.Annuaire
import fr.coprofirst.app.commun.annuaire.lu
import fr.coprofirst.app.session.SessionOuverte

/** Ce que l'Annuaire demande à la résidence. */
interface ServiceAnnuaire {
    suspend fun annuaire(): Annuaire
}

/** L'annuaire par le client généré, à travers LA porte de la résidence. */
class ServiceAnnuaireApi(private val session: SessionOuverte) : ServiceAnnuaire {
    override suspend fun annuaire() = session.appeler { b, c -> AdminApi(b, c).getAdminAnnuaire().lu() }
}
