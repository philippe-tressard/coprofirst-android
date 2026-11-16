// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.notifications.push

import android.content.Context
import android.content.pm.ApplicationInfo
import fr.coprofirst.api.modeles.AppareilEnregistre
import fr.coprofirst.api.routes.AppareilsApi
import fr.coprofirst.app.session.SessionOuverte

/**
 * Le service à viser : celui de développement pour une app signée au poste (débogable),
 * celui de production pour la version distribuée — la colonne `environnement` de la table
 * `appareil` (spec push §6.1), comme l'iOS (`EnvironnementAPNs`).
 */
enum class EnvironnementPush(val servi: AppareilEnregistre.Environnement) {
    PRODUCTION(AppareilEnregistre.Environnement.PRODUCTION),
    DEVELOPPEMENT(AppareilEnregistre.Environnement.DEVELOPPEMENT),
    ;

    companion object {
        fun de(contexte: Context): EnvironnementPush =
            if (contexte.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) DEVELOPPEMENT else PRODUCTION
    }
}

/** Ce que l'app envoie à POST /appareils : le jeton, le service à viser, l'abonnement opaque — rien d'autre. */
data class AppareilPush(val jeton: JetonPush, val environnement: EnvironnementPush, val abonnement: String)

/** Les appareils de la personne connectée (spec push §6.2), comme l'iOS (`ServiceAppareils`). */
interface ServiceAppareils {
    /** POST /appareils : enregistre ou rafraîchit (`vu_le`) — 60 jours sans, l'installation le purge. */
    suspend fun enregistrer(appareil: AppareilPush, session: SessionOuverte)

    /** DELETE /appareils/{abonnement} : l'abonnement d'un autre compte rend 404. */
    suspend fun retirer(abonnement: String, session: SessionOuverte)
}

/** Les appareils par le client GÉNÉRÉ. */
object ServiceAppareilsApi : ServiceAppareils {
    override suspend fun enregistrer(appareil: AppareilPush, session: SessionOuverte) {
        val corps = AppareilEnregistre(appareil.jeton.valeur, appareil.environnement.servi, appareil.abonnement)
        session.appeler { base, client -> AppareilsApi(base, client).postAppareils(corps) }
    }

    override suspend fun retirer(abonnement: String, session: SessionOuverte) {
        session.appeler { base, client -> AppareilsApi(base, client).deleteAppareilsAbonnement(abonnement) }
    }
}
