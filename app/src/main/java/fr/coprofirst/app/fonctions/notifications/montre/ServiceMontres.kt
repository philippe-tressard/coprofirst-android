// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.notifications.montre

import fr.coprofirst.api.modeles.MontreDemandee
import fr.coprofirst.api.modeles.MontreEmise
import fr.coprofirst.api.routes.MontresApi
import fr.coprofirst.app.session.SessionOuverte
import java.time.Instant

/** Un jeton de montre émis ou renouvelé (`MontreEmise`) : le brut n'est rendu qu'à ce moment. */
class JetonDeMontre(val id: Int, val jeton: String, val expireLe: Instant) {
    override fun toString() = "JetonDeMontre(id=$id, jeton=•••, expireLe=$expireLe)"

    companion object {
        /** La seule lecture du type généré (`MontreEmise`). */
        fun depuis(m: MontreEmise) = JetonDeMontre(m.id, m.jeton, m.expireLe.toInstant())
    }
}

/** Les montres du compte connecté (coprofirst#2771), demandées par le TÉLÉPHONE seul. */
interface ServiceMontres {
    /** POST /montres : un jeton neuf pour une montre Wear OS (au plus 4 par compte). */
    suspend fun emettre(session: SessionOuverte): JetonDeMontre

    /** POST /montres/{id}/renouveler : un jeton neuf, 30 jours de plus. */
    suspend fun renouveler(id: Int, session: SessionOuverte): JetonDeMontre

    /** DELETE /montres/{id} : la montre ne lit plus rien. */
    suspend fun retirer(id: Int, session: SessionOuverte)
}

/** Par le client généré, sur la session de la résidence. */
object ServiceMontresApi : ServiceMontres {
    override suspend fun emettre(session: SessionOuverte) = session.appeler { base, client ->
        JetonDeMontre.depuis(MontresApi(base, client).postMontres(MontreDemandee(MontreDemandee.Plateforme.WEAROS)))
    }

    override suspend fun renouveler(id: Int, session: SessionOuverte) = session.appeler { base, client ->
        JetonDeMontre.depuis(MontresApi(base, client).postMontresMontreIdRenouveler(id))
    }

    override suspend fun retirer(id: Int, session: SessionOuverte) {
        session.appeler { base, client -> MontresApi(base, client).deleteMontresMontreId(id) }
    }
}
