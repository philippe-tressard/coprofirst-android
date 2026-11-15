// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.session

import fr.coprofirst.api.VERSION_CONTRAT_PLANCHER

/**
 * La version du contrat d'API servie par une installation (`version_contrat` de
 * GET /config), lue par ses trois nombres. Illisible ou absente : `null` — l'app ne
 * devine pas une version.
 */
data class VersionContrat(val majeure: Int, val mineure: Int, val correctif: Int) : Comparable<VersionContrat> {
    override fun compareTo(other: VersionContrat): Int =
        compareValuesBy(this, other, { it.majeure }, { it.mineure }, { it.correctif })

    override fun toString(): String = "$majeure.$mineure.$correctif"

    companion object {
        private val FORME = Regex("""(\d+)\.(\d+)\.(\d+)""")

        fun lire(texte: String?): VersionContrat? {
            val trouve = texte?.trim()?.let { FORME.matchEntire(it) } ?: return null
            val (a, b, c) = trouve.destructured
            return runCatching { VersionContrat(a.toInt(), b.toInt(), c.toInt()) }.getOrNull()
        }

        /** Le contrat sur lequel le client est généré : le plancher (client-api). */
        val PLANCHER: VersionContrat = requireNotNull(lire(VERSION_CONTRAT_PLANCHER))
    }
}
