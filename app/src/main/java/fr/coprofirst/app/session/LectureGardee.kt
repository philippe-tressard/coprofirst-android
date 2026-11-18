// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.session

import fr.coprofirst.api.modeles.LoginRequest
import fr.coprofirst.api.routes.AdminApi
import fr.coprofirst.api.routes.AuthApi
import fr.coprofirst.api.routes.DocumentsApi
import fr.coprofirst.api.routes.FluxApi
import fr.coprofirst.api.routes.LotsApi
import fr.coprofirst.api.routes.TicketsApi
import fr.coprofirst.app.session.ListeDesLectures.Companion.ID
import fr.coprofirst.app.session.ListeDesLectures.Companion.muet

/**
 * CE QUE LE TÉLÉPHONE GARDE, et rien d'autre : la liste blanche des lectures de l'iOS
 * (`LectureGardee.operations`, #101), par leur chemin sous `/api` — le fil, les affaires
 * (liste, ce qu'il faut pour la lire, détail, évolutions, messages), le profil, l'annuaire
 * que tout résident lit, un document déjà ouvert —, et les fichiers protégés (`/uploads/`)
 * déjà ouverts. Aucune route de l'administration ni de l'Espace CS ; une route absente
 * d'ici n'est jamais écrite sur l'appareil.
 */
val LectureGardee = ListeDesLectures(
    routes = listOf(
        FluxApi("", muet).getFluxRequestConfig(null, null, null).path,
        TicketsApi("", muet).getTicketsRequestConfig(null).path,
        TicketsApi("", muet).getTicketsReferentielsRequestConfig(null).path,
        LotsApi("", muet).getLotsMesPerimetresRequestConfig(null).path,
        TicketsApi("", muet).getTicketsTicketIdRequestConfig(ID, null).path,
        TicketsApi("", muet).getTicketsTicketIdEvolutionsRequestConfig(ID, null).path,
        TicketsApi("", muet).getTicketsTicketIdMessagesRequestConfig(ID, null).path,
        AuthApi("", muet).getAuthMeRequestConfig(null).path,
        // La seule sous l'administration : l'annuaire que TOUT résident lit (aucun rôle demandé).
        AdminApi("", muet).getAdminAnnuaireRequestConfig(null).path,
        DocumentsApi("", muet).getDocumentsDocIdTlchargerRequestConfig(ID, null).path,
    ),
    fichiers = true,
    session = listOf(
        AuthApi("", muet).postAuthRefreshRequestConfig(null).path,
        AuthApi("", muet).postAuthLoginRequestConfig(LoginRequest("", "")).path,
        AuthApi("", muet).postAuthLogoutRequestConfig(null).path,
    ),
    deconnexion = AuthApi("", muet).postAuthLogoutRequestConfig(null).path,
)
