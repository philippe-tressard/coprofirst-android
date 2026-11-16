// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.montre

import fr.coprofirst.app.fonctions.notifications.montre.LiaisonMontre
/** Ce que la montre montre en s'ouvrant. La cloche elle-même vient avec les écrans (#207, lot 3). */
sealed interface EtatMontre {
    /** Jamais reliée, ou liaison éteinte : « Ouvrez CoproFirst sur le téléphone pour relier la montre ». */
    data object NonReliee : EtatMontre

    /** Reliée par le téléphone à la résidence nommée — le nom vient du téléphone, jamais du jeton. */
    data class Reliee(val residence: String) : EtatMontre

    companion object {
        /**
         * L'état au lancement : la résidence gardée de la dernière liaison, s'il y en a une.
         * Drapeau éteint, une liaison gardée ne compte pas : rien ne s'ouvre.
         */
        fun auLancement(residenceGardee: String?, actif: Boolean = LiaisonMontre.ACTIF): EtatMontre =
            if (actif && !residenceGardee.isNullOrBlank()) Reliee(residenceGardee) else NonReliee
    }
}
