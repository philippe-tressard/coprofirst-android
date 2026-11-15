// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.perimetres

import fr.coprofirst.api.modeles.LibellePerimetreEntree
import fr.coprofirst.api.routes.PerimetresApi
import fr.coprofirst.app.session.SessionOuverte

/**
 * Ce que le choix des périmètres demande au serveur, comme l'iOS (`ServicePerimetres`, et
 * `ServiceApercus.lieu`) : l'arbre, et le nom d'une sélection en cours de saisie. Un écran
 * qui choisit un lieu le reçoit ; son service de menu peut aussi le réaliser par délégation.
 *
 * ⚠️ Divergence déclarée : l'iOS range `lieu` dans `ServiceApercus`, avec « qui la lira » et
 * « Nom affiché », que l'Android ne porte pas encore — il vit ici, près de son seul usage.
 */
interface ServicePerimetres {
    /** GET /perimetres : les périmètres ACTIFS, dans l'ordre de l'arbre servi. */
    suspend fun perimetres(): List<Perimetre>

    /**
     * POST /perimetres/libelle (#130 de l'iOS) : le lieu d'une sélection, nommé par le
     * serveur — même le périmètre par défaut. L'app ne compose jamais ce nom.
     */
    suspend fun lieu(codes: List<String>): String
}

class ServicePerimetresApi(private val session: SessionOuverte) : ServicePerimetres {
    override suspend fun perimetres() =
        session.appeler { base, client -> Perimetre.lus(PerimetresApi(base, client).getPerimetres()) }

    override suspend fun lieu(codes: List<String>) =
        session.appeler { base, client -> PerimetresApi(base, client).postPerimetresLibelle(LibellePerimetreEntree(codes)).libelle }
}
