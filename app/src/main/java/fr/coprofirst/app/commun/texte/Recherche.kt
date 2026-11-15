// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.texte

import java.text.Normalizer
import java.util.Locale

/**
 * La recherche d'une liste, comme l'iOS (`Recherche`, `contientTousLesMots` de
 * `#lib/texte.ts` du site) : tous les mots saisis, dans n'importe quel ordre, sans accents
 * ni casse, sur ce que la ligne affiche.
 */
object Recherche {
    private val DIACRITIQUES = Regex("\\p{Mn}+")
    private val BLANCS = Regex("[\\s\\p{Z}]+")

    fun contientTousLesMots(champs: List<String?>, recherche: String): Boolean {
        val mots = replier(recherche).split(BLANCS).filter { it.isNotEmpty() }
        if (mots.isEmpty()) return true
        val texte = replier(champs.filterNotNull().joinToString(" "))
        return mots.all { it in texte }
    }

    /** Sans accents ni casse, à la française. */
    fun replier(texte: String): String =
        DIACRITIQUES.replace(Normalizer.normalize(texte, Normalizer.Form.NFD), "").lowercase(Locale.FRENCH)
}
