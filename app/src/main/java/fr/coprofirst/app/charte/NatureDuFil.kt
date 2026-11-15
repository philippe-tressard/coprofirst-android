// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.charte

import androidx.compose.ui.graphics.Color

/**
 * Ce que le fil montre d'un type d'élément, comme l'iOS (`NatureDuFil`) : son libellé, sa
 * teinte et son fond — deux JETONS de la palette (`Couleurs::filRouge`), lus dans la palette
 * en cours (`LocalCouleurs.current`), jamais une couleur écrite —, son pictogramme (nom
 * Lucide, `Pictogrammes`). La table est générée : `NaturesDuFil.parType`.
 */
data class NatureDuFil(
    val libelle: String,
    val couleur: (Couleurs) -> Color,
    val fond: (Couleurs) -> Color,
    val icone: String,
) {
    companion object {
        /** Un type que la table ne connaît pas : neutre, jamais une erreur (le libellé de l'iOS). */
        val inconnue = NatureDuFil("Élément", Couleurs::filInconnu, Couleurs::filInconnuFond, "circle")

        fun de(type: String): NatureDuFil = NaturesDuFil.parType[type] ?: inconnue
    }
}

/**
 * Une nature d'affaire (`NATURES` du site : Actualité, Calendrier, Affaire), comme l'iOS
 * (`NatureAffaire`) : son code servi, son libellé entier (pas l'abrégé de la gouttière du
 * site), son pictogramme, et la classe `badge-*` de sa teinte (`PastilleTeinte`). La table est
 * générée, dans l'ordre du site : `NaturesAffaire.toutes`.
 */
data class NatureAffaire(val code: String, val libelle: String, val icone: String, val badge: String)
