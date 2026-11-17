// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.montre

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import fr.coprofirst.montre.cloche.LectureCloche
import fr.coprofirst.montre.liaison.EtatLecture

/** Ce que l'écran montre : l'état de la montre et sa cloche. */
data class Vue(val etat: EtatMontre, val lecture: LectureCloche)

/**
 * La montre à son ouverture : d'abord la liaison gardée (`depart`, la cloche se charge), puis
 * ce que la lecture rapporte (`LectureMontre`) — la cloche, la dernière gardée hors ligne,
 * ou le retour à « relier la montre » quand le jeton n'ouvre plus rien. Rien ne se marque
 * comme lu.
 */
@Composable
fun PorteMontre(depart: EtatMontre, lire: suspend () -> EtatLecture) {
    val vue by produceState(Vue(depart, LectureCloche.EnCours)) {
        if (depart is EtatMontre.Reliee) value = vueDe(lire())
    }
    EcranMontre(vue.etat, vue.lecture)
}

/** Ce que la lecture rapporte, en ce que l'écran montre. */
fun vueDe(lecture: EtatLecture): Vue = when (lecture) {
    EtatLecture.NonReliee -> Vue(EtatMontre.NonReliee, LectureCloche.EnCours)
    is EtatLecture.Lue -> Vue(EtatMontre.Reliee(lecture.adresse, lecture.residence), LectureCloche.Lue(lecture.cloche))
    is EtatLecture.HorsLigne ->
        Vue(EtatMontre.Reliee(lecture.adresse, lecture.residence), LectureCloche.HorsLigne(lecture.cloche, lecture.aJourAu))
    is EtatLecture.Injoignable -> Vue(EtatMontre.Reliee(lecture.adresse, lecture.residence), LectureCloche.Injoignable)
}
