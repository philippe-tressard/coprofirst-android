// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.notifications

import fr.coprofirst.api.modeles.NotificationRead
import fr.coprofirst.api.modeles.SuppressionNotifications
import fr.coprofirst.api.routes.NotificationsApi
import fr.coprofirst.app.session.SessionOuverte

/** Ce que l'écran des notifications demande à la résidence ouverte, comme l'iOS (`ServiceNotifications`). */
interface ServiceNotifications {
    /** GET /notifications : toutes, les plus récentes d'abord (le serveur trie). */
    suspend fun notifications(): List<NotificationRecue>

    /** PATCH /notifications/{id}/lue. */
    suspend fun marquerLue(id: Int)

    /** POST /notifications/tout-marquer-lu. */
    suspend fun toutMarquerLu()

    /** DELETE /notifications/{id} — définitif, comme sur le site. */
    suspend fun supprimerNotification(id: Int)

    /**
     * POST /notifications/supprimer-selection — celles choisies, en UNE requête. Un
     * identifiant qui n'est pas une alerte du compte est ignoré par le serveur. Le contrat
     * borne la requête à `NOTIFICATIONS_SELECTION_PAR_REQUETE` identifiants (422 au-delà) :
     * `ParcoursNotifications.supprimer` découpe une sélection plus grande.
     */
    suspend fun supprimerNotifications(ids: Set<Int>)

    /** POST /notifications/supprimer-lues — toutes les lues du compte, en une requête. */
    suspend fun supprimerLues()
}

/** Le service par le client généré, sur la session de la résidence. */
class ServiceNotificationsApi(private val session: SessionOuverte) : ServiceNotifications {
    private suspend fun <R> api(requete: NotificationsApi.() -> R): R =
        session.appeler { base, client -> NotificationsApi(base, client).requete() }

    override suspend fun notifications() = api { getNotifications().map(NotificationRecue::depuis) }

    override suspend fun marquerLue(id: Int) {
        api { patchNotificationsNotifIdLue(id) }
    }

    override suspend fun toutMarquerLu() {
        api { postNotificationsToutMarquerLu() }
    }

    override suspend fun supprimerNotification(id: Int) = api { deleteNotificationsNotifId(id) }

    override suspend fun supprimerNotifications(ids: Set<Int>) {
        api { postNotificationsSupprimerSelection(SuppressionNotifications(ids.sorted())) }
    }

    override suspend fun supprimerLues() {
        api { postNotificationsSupprimerLues() }
    }
}
