// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.fil

import fr.coprofirst.app.commun.fil.ElementDuFil
import fr.coprofirst.app.commun.fil.JourDuFil
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs

/** Un jour de la semaine du carnet : sa date, ses cartes au jour SERVI (aucune : « Rien ce jour »). */
data class JourDeLaSemaine(val jour: LocalDate, val elements: List<ElementDuFil>)

/**
 * La semaine du carnet (#286), comme `SemaineDuFil` de l'iOS (coprofirst-ios#277) : du lundi au
 * dimanche — du lundi au jeudi à gauche du pli, du vendredi au dimanche à droite. Chaque carte
 * se range au jour que l'API lui SERT (`ElementDuFil.jour`, `GET /flux?du=&au=`) : rien n'est
 * recomposé ici (décision du 17/11/2026).
 */
object SemaineDuFil {
    /** Les jours de la page de gauche (lundi → jeudi) ; les autres vont à droite. */
    const val A_GAUCHE = 4

    /** Le lundi de la semaine de `jour`. */
    fun lundi(jour: LocalDate): LocalDate = jour.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** Les sept jours de la semaine, chacun avec ses cartes, de la plus récente à la plus ancienne. */
    fun ranger(lundi: LocalDate, elements: List<ElementDuFil>): List<JourDeLaSemaine> {
        val parJour = elements.groupBy { it.jour }
        return (0L until JOURS).map { decalage ->
            val jour = lundi.plusDays(decalage)
            JourDeLaSemaine(jour, parJour[jour.toString()].orEmpty().sortedByDescending { it.date })
        }
    }

    /** La semaine d'avant ; aucune avant celle du plus ancien jour du fil. */
    fun precedente(lundi: LocalDate, jours: List<JourDuFil>): LocalDate? {
        val plusAncien = jours.lastOrNull()?.let { lundi(LocalDate.parse(it.jour)) } ?: return null
        return lundi.minusWeeks(1).takeUnless { it.isBefore(plusAncien) }
    }

    /** La semaine d'après (une échéance peut y tomber). */
    fun suivante(lundi: LocalDate): LocalDate = lundi.plusWeeks(1)

    /**
     * Le jour du fil à rouvrir pour `jour` : lui-même s'il a des cartes publiées, sinon le jour
     * d'activité le plus proche (le plus récent à égalité). `null` : un fil vide.
     */
    fun jourDuFil(jours: List<JourDuFil>, jour: LocalDate): String? =
        jours.minWithOrNull(compareBy<JourDuFil> { abs(ChronoUnit.DAYS.between(jour, LocalDate.parse(it.jour))) }.thenByDescending { it.jour })?.jour

    /** Le jour du fil d'une carte touchée dans la semaine : celui où le fil la publie, le plus proche de son jour servi sinon. */
    fun jourDeLaCarte(jours: List<JourDuFil>, element: ElementDuFil, jour: LocalDate): String? =
        jours.firstOrNull { j -> j.elements.any { it.id == element.id } }?.jour ?: jourDuFil(jours, jour)

    private const val JOURS = 7L
}
