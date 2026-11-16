// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.notifications

import fr.coprofirst.api.modeles.NotificationRead
import java.time.Instant

/** Une alerte reçue, telle que GET /notifications la sert (`NotificationRead`). */
data class NotificationRecue(
    val id: Int,
    val type: String,
    val titre: String,
    /** Du HTML de l'éditeur du site : il passe par `TexteRiche`. */
    val corps: String,
    val lue: Boolean,
    val urgente: Boolean,
    val creeLe: Instant,
) {
    companion object {
        fun depuis(n: NotificationRead) = NotificationRecue(n.id, n.type, n.titre, n.corps, n.lue, n.urgente, n.creeLe.toInstant())
    }
}
