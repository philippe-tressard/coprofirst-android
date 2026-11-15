// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun

import fr.coprofirst.api.modeles.SignalementCreate
import fr.coprofirst.api.routes.SignalementsApi
import fr.coprofirst.app.session.SessionOuverte

/**
 * Ce qu'on signale au conseil syndical : `cible_type` et `cible_id` de POST /signalements
 * (`SignalementCreate`), comme l'iOS (`CibleSignalement`). Le type est celui que le site
 * envoie (`signaler('commentaire', id)`, `lib/signalements.ts`).
 */
data class CibleSignalement(val type: String, val id: Int) {
    companion object {
        /** Une réponse d'une idée, d'une annonce, ou le commentaire d'un sondage. */
        fun commentaire(id: Int) = CibleSignalement("commentaire", id)
    }
}

/**
 * POST /signalements : un motif, au conseil syndical — comme l'iOS (`ServiceSignalements`).
 * Plusieurs lecteurs (la Communauté, la fiche d'un sondage, les affaires, le fil) : leurs
 * services en héritent.
 */
interface ServiceSignalements {
    /** @throws fr.coprofirst.app.session.ErreurService la phrase du serveur s'il refuse. */
    suspend fun signalerAuConseil(cible: CibleSignalement, motif: String)
}

/** Le signalement par le client GÉNÉRÉ, à travers la porte de la résidence. */
class ServiceSignalementsApi(private val session: SessionOuverte) : ServiceSignalements {
    override suspend fun signalerAuConseil(cible: CibleSignalement, motif: String) {
        val corps = SignalementCreate(cibleType = cible.type, cibleId = cible.id, motif = motif)
        session.appeler { base, client -> SignalementsApi(base, client).postSignalements(corps) }
    }
}
