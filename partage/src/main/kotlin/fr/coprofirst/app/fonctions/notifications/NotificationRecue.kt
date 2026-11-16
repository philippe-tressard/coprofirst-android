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
    /**
     * Le chemin de l'entité sur le site (« /tickets/12 »), ou rien. La montre l'ouvre sur le
     * téléphone (« Continuer sur le téléphone », #207) ; elle ne le suit jamais elle-même.
     */
    val lien: String? = null,
) {
    companion object {
        fun depuis(n: NotificationRead) = NotificationRecue(n.id, n.type, n.titre, n.corps, n.lue, n.urgente, n.creeLe.toInstant(), n.lien)
    }
}
