// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.formats

import java.time.temporal.ChronoUnit

/**
 * Les mots d’une date relative, en français, comme le formateur relatif de l’iOS (style
 * `.named`) : un écart d'UNE unité porte son nom (« hier », « le mois dernier »),
 * deux jours aussi (« avant-hier », « après-demain ») ; sinon « il y a N … » ou « dans N … ».
 * Seul `FormatsDuSite.relatif` s'en sert.
 */
internal object Relatif {
    private class Mots(val un: String, val plusieurs: String, val hier: String? = null, val demain: String? = null)

    private val mots = mapOf(
        ChronoUnit.YEARS to Mots("an", "ans", "l’année dernière", "l’année prochaine"),
        ChronoUnit.MONTHS to Mots("mois", "mois", "le mois dernier", "le mois prochain"),
        ChronoUnit.WEEKS to Mots("semaine", "semaines", "la semaine dernière", "la semaine prochaine"),
        ChronoUnit.DAYS to Mots("jour", "jours", "hier", "demain"),
        ChronoUnit.HOURS to Mots("heure", "heures"),
        ChronoUnit.MINUTES to Mots("minute", "minutes"),
        ChronoUnit.SECONDS to Mots("seconde", "secondes"),
    )

    fun dire(unite: ChronoUnit, n: Long, passe: Boolean): String {
        val m = mots.getValue(unite)
        if (n == 1L) (if (passe) m.hier else m.demain)?.let { return it }
        if (n == 2L && unite == ChronoUnit.DAYS) return if (passe) "avant-hier" else "après-demain"
        val quantite = "$n ${if (n == 1L) m.un else m.plusieurs}"
        return if (passe) "il y a $quantite" else "dans $quantite"
    }
}
