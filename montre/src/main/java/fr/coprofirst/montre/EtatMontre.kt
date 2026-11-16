// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.montre

/** Ce que la montre montre en s'ouvrant. La cloche elle-même vient avec les écrans (#207, lot 3). */
sealed interface EtatMontre {
    /** Jamais reliée, ou liaison éteinte : « Ouvrez CoproFirst sur le téléphone pour relier la montre ». */
    data object NonReliee : EtatMontre

    /**
     * Reliée par le téléphone : l'adresse de la résidence et son nom. Tous deux viennent du
     * téléphone à l'appairage, jamais du jeton.
     */
    data class Reliee(val adresse: String, val residence: String) : EtatMontre

    companion object {
        /**
         * L'état au lancement : la liaison gardée, s'il y en a une.
         * Drapeau éteint, une liaison gardée ne compte pas : rien ne s'ouvre.
         */
        fun auLancement(liaison: Reliee?, actif: Boolean = LiaisonMontre.ACTIF): EtatMontre =
            if (actif && liaison != null && liaison.adresse.isNotBlank() && liaison.residence.isNotBlank()) liaison else NonReliee
    }
}
