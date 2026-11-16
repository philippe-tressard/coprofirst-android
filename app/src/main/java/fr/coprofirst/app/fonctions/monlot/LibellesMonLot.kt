// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.monlot

import fr.coprofirst.app.commun.LibelleTeinte

/**
 * Les libellés de « Mes lots & accès », ceux des tables GÉNÉRÉES (`TablesMonLot`, #74).
 * Une valeur inconnue
 * s'affiche brute : un libellé manquant doit se voir. Ceux d'un accès (type, état) sont
 * communs : `LibellesAcces` (commun/acces).
 */
object LibellesMonLot {
    fun statutBail(code: String): LibelleTeinte = TablesMonLot.statutsBail[code] ?: LibelleTeinte.brut(code)
    fun statutObjet(code: String): LibelleTeinte = TablesMonLot.statutsObjet[code] ?: LibelleTeinte.brut(code)
    fun typeObjet(code: String): String = TablesMonLot.typesObjet[code] ?: code

    /** La gravité d'un constat ; inconnue, brute. */
    fun gravite(code: String): LibelleTeinte = TablesMonLot.gravites[code] ?: LibelleTeinte.brut(code)

    /** Les constats, ce qui bloque d'abord (`ConstatsLots.svelte`). */
    fun ordonner(constats: List<ConstatDuLot>): List<ConstatDuLot> = constats.sortedBy { c ->
        TablesMonLot.gravites.keys.indexOf(c.gravite).takeIf { it >= 0 } ?: Int.MAX_VALUE
    }
}

