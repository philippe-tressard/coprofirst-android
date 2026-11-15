// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.monlot

import fr.coprofirst.app.commun.LibelleTeinte

/**
 * Les libellés de « Mes lots & accès » : ceux des tables GÉNÉRÉES (`TablesMonLot`), et
 * ceux que le site écrit dans un composant (`ProvisoireMonLot`). Une valeur inconnue
 * s'affiche brute : un libellé manquant doit se voir.
 */
object LibellesMonLot {
    fun statutAcces(code: String): LibelleTeinte = TablesMonLot.statutsAcces[code] ?: LibelleTeinte.brut(code)
    fun statutBail(code: String): LibelleTeinte = TablesMonLot.statutsBail[code] ?: LibelleTeinte.brut(code)
    fun statutObjet(code: String): LibelleTeinte = ProvisoireMonLot.statutsObjet[code] ?: LibelleTeinte.brut(code)
    fun typeAcces(code: String): String = TablesMonLot.typesAcces.firstOrNull { it.first == code }?.second ?: code
    fun typeObjet(code: String): String = ProvisoireMonLot.typesObjet[code] ?: code

    /** La gravité d'un constat ; inconnue, brute. */
    fun gravite(code: String): LibelleTeinte = ProvisoireMonLot.gravites.firstOrNull { it.first == code }?.second ?: LibelleTeinte.brut(code)

    /** Les constats, ce qui bloque d'abord (`ConstatsLots.svelte`). */
    fun ordonner(constats: List<ConstatDuLot>): List<ConstatDuLot> = constats.sortedBy { c ->
        ProvisoireMonLot.gravites.indexOfFirst { it.first == c.gravite }.takeIf { it >= 0 } ?: Int.MAX_VALUE
    }
}

/**
 * ⚠️ PROVISOIRE : trois tables que le site écrit DANS un composant Svelte, et que
 * `tables-clients.json` ne publie pas encore — recopiées à l'identique, comme l'iOS
 * (`ProvisoireAcces`, coprofirst v3.61.0). coprofirst#2756 demande de les publier ;
 * elles se généreront alors comme `TablesMonLot`.
 */
object ProvisoireMonLot {
    /** Le type d'un objet remis au locataire — `components/InventaireBail.svelte`. */
    val typesObjet: Map<String, String> = mapOf(
        "cle" to "🔑 Clé", "telecommande" to "📡 Télécommande", "vigik" to "🏷️ Vigik", "autre" to "📦 Autre",
    )

    /** Le statut d'un objet remis — `components/InventaireBail.svelte`. */
    val statutsObjet: Map<String, LibelleTeinte> = mapOf(
        "en_possession" to LibelleTeinte("En possession", "badge-green"),
        "rendu" to LibelleTeinte("Rendu", "badge-blue"),
        "perdu" to LibelleTeinte("Perdu", "badge-red"),
        "non_remis" to LibelleTeinte("Non remis", "badge-gray"),
    )

    /** La gravité d'un constat sur un lot, du plus grave au moins grave — `components/ConstatsLots.svelte`. */
    val gravites: List<Pair<String, LibelleTeinte>> = listOf(
        "bloquant" to LibelleTeinte("Bloquant", "badge-red"),
        "inconnu" to LibelleTeinte("Inconnu", "badge-gray"),
        "avertissement" to LibelleTeinte("À savoir", "badge-orange"),
        "information" to LibelleTeinte("Information", "badge-blue"),
    )
}
