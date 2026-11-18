// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.session.audience

import fr.coprofirst.api.modeles.EvenementAudience
import fr.coprofirst.api.modeles.LotAudience
import fr.coprofirst.api.routes.TelemetryApi
import fr.coprofirst.app.session.SessionOuverte

/**
 * Ce que la mesure d'audience demande à une résidence (#29, coprofirst#2726) : poster un lot
 * de pages vues, POST /telemetry/collect, en déclarant le canal de l'app — comme l'iOS
 * (`ServiceAudience`).
 */
fun interface ServiceAudience {
    /**
     * Un lot de pages vues, à la résidence de `session` — au plus
     * `LimitesAudience.EVENEMENTS_PAR_LOT`, chacune de `LimitesAudience.PAGE` caractères au
     * plus : au-delà, le serveur refuse le lot entier.
     */
    suspend fun envoyer(pages: List<String>, session: SessionOuverte)
}

/**
 * La mesure par le client GÉNÉRÉ : le lot est typé, canal compris. `android` range ses vues
 * sous « App Android » au tableau de bord du site ; le canal ne s'écrit pas, il se choisit
 * dans l'énumération générée — une valeur hors de la liste du serveur serait refusée (422).
 *
 * Chaque lot porte la CLASSE de l'appareil (#77, coprofirst#2774), relue à chaque envoi
 * (`appareil`) : une valeur de `AppareilAudience`, générée depuis la liste du serveur
 * (`appareilsParCanal`), jamais écrite à la main. Le refus de la mesure coupe tout, elle
 * comprise : rien n'arrive ici (`MesureAudience`).
 */
class ServiceAudienceApi(private val appareil: () -> AppareilAudience) : ServiceAudience {
    override suspend fun envoyer(pages: List<String>, session: SessionOuverte) {
        val lot = LotAudience(
            events = pages.map { EvenementAudience(page = it, action = MesureAudience.ACTION) },
            canal = LotAudience.Canal.ANDROID,
            appareil = appareil().code,
        )
        session.appeler { base, client -> TelemetryApi(base, client).postTelemetryCollect(lot) }
    }
}
