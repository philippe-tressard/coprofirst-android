// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.montre.tuile

import fr.coprofirst.montre.cloche.Cloche
import fr.coprofirst.montre.liaison.EtatLecture

/**
 * Ce que dit la tuile, comme le widget de l'Apple Watch (coprofirst-ios#226, 16/11/2026) :
 * « 2 nouvelles alertes · dont 1 urgente ». Ni titre, ni corps, ni nom de résidence : la
 * tuile se voit d'un coup d'œil, par d'autres aussi.
 */
sealed interface EtatTuile {
    /** La montre n'est pas reliée (ou la liaison est éteinte). */
    data object NonReliee : EtatTuile

    /** Les non lues de la dernière lecture, et combien sont urgentes. */
    data class Compte(val nonLues: Int, val urgentes: Int) : EtatTuile

    /** Sans réseau et sans lecture gardée : la résidence ne répond pas. */
    data object Injoignable : EtatTuile

    companion object {
        /** Depuis la dernière cloche lue ; sans lecture (montre pas reliée), « non reliée ». */
        fun de(cloche: Cloche?): EtatTuile = cloche?.let { Compte(it.nonLues.size, it.urgentes) } ?: NonReliee

        /** Depuis la lecture de la montre : en ligne ou hors ligne, la tuile compte la même cloche. */
        fun de(lecture: EtatLecture): EtatTuile = when (lecture) {
            EtatLecture.NonReliee -> NonReliee
            is EtatLecture.Lue -> de(lecture.cloche)
            is EtatLecture.HorsLigne -> de(lecture.cloche)
            is EtatLecture.Injoignable -> Injoignable
        }
    }
}

/** Les libellés dont la phrase se compose : ceux des ressources, au pluriel d'Android. */
class LibellesTuile(
    val nonReliee: String,
    val injoignable: String,
    val aucune: String,
    val alertes: (Int) -> String,
    val dontUrgentes: (Int) -> String,
    /** « 2 nouvelles alertes » + « dont 1 urgente », par le séparateur du site (ressource). */
    val joindre: (String, String) -> String,
)

object PhraseTuile {
    fun de(etat: EtatTuile, libelles: LibellesTuile): String = when (etat) {
        EtatTuile.NonReliee -> libelles.nonReliee
        EtatTuile.Injoignable -> libelles.injoignable
        is EtatTuile.Compte -> when {
            etat.nonLues <= 0 -> libelles.aucune
            etat.urgentes <= 0 -> libelles.alertes(etat.nonLues)
            else -> libelles.joindre(libelles.alertes(etat.nonLues), libelles.dontUrgentes(etat.urgentes))
        }
    }
}
