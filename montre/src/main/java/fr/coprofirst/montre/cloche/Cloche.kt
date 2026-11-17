// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.montre.cloche

import fr.coprofirst.app.fonctions.notifications.NotificationRecue
import java.time.Instant

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

/** Ce que la montre sait de la cloche à un instant. */
sealed interface LectureCloche {
    data object EnCours : LectureCloche

    data class Lue(val cloche: Cloche) : LectureCloche

    /** Sans réseau : la dernière lecture gardée, datée, comme le téléphone (#28). */
    data class HorsLigne(val cloche: Cloche, val aJourAu: Instant) : LectureCloche

    /** Sans réseau, et rien de gardé : la résidence ne répond pas. */
    data object Injoignable : LectureCloche
}
