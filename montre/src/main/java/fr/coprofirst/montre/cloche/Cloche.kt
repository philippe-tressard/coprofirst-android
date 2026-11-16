// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.montre.cloche

import fr.coprofirst.app.fonctions.notifications.NotificationRecue

/**
 * La cloche telle que la montre la range (périmètre du 16/11/2026, coprofirst-ios#226) :
 * d'abord les non lues, les URGENTES devant ; puis les déjà lues. Dans chaque groupe,
 * l'ordre du serveur (les plus récentes d'abord) est gardé.
 */
class Cloche private constructor(val nonLues: List<NotificationRecue>, val lues: List<NotificationRecue>) {
    /** Les non lues urgentes : la tuile les compte (« dont 1 urgente »). */
    val urgentes: Int get() = nonLues.count { it.urgente }

    val vide: Boolean get() = nonLues.isEmpty() && lues.isEmpty()

    fun alerte(id: Int): NotificationRecue? = nonLues.firstOrNull { it.id == id } ?: lues.firstOrNull { it.id == id }

    companion object {
        fun de(alertes: List<NotificationRecue>): Cloche {
            val (lues, nonLues) = alertes.partition { it.lue }
            // `sortedByDescending` est stable : entre deux urgentes, l'ordre du serveur reste.
            return Cloche(nonLues.sortedByDescending { it.urgente }, lues)
        }
    }
}

/** Ce que la montre sait de la cloche à un instant. Le hors-ligne daté vient au lot 6 (#207). */
sealed interface LectureCloche {
    data object EnCours : LectureCloche

    data class Lue(val cloche: Cloche) : LectureCloche

    /**
     * La résidence n'a pas répondu. DIVERGENCE déclarée (#207, lot 6) : la dernière lecture,
     * datée (« à jour à 14:32 »), vient avec le hors-ligne et ses libellés partagés (#206).
     */
    data object Injoignable : LectureCloche
}
