// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.notifications.montre

/**
 * Ce que le téléphone transmet à la montre Wear OS (#207, lot 2b), comme WatchConnectivity
 * de l'iPhone à l'Apple Watch : la résidence (adresse, nom) et le jeton de montre
 * (coprofirst#2771 : `GET /notifications` seul, révocable, 30 jours). Ou l'effacement, à la
 * déconnexion. Écrit UNE fois pour les deux côtés : le téléphone l'écrit, la montre le lit.
 *
 * Le transport (Data Layer de Wear OS) ne connaît que des chaînes : `versDonnees`/`depuis`.
 */
sealed interface MessageLiaison {
    data class Liaison(val adresse: String, val residence: String, val jeton: String, val expireLe: String) : MessageLiaison {
        /** Le jeton n'apparaît dans aucune trace. */
        override fun toString() = "Liaison(adresse=$adresse, residence=$residence, jeton=•••, expireLe=$expireLe)"
    }

    /** La résidence se ferme sur le téléphone : la montre oublie la liaison de cette adresse. */
    data class Effacement(val adresse: String) : MessageLiaison

    companion object {
        /** Le chemin de l'élément de données que la montre écoute. */
        const val CHEMIN = "/coprofirst/liaison"

        /** La capacité que la montre déclare : le téléphone ne parle qu'à une montre qui a l'app. */
        const val CAPACITE = "coprofirst_montre"

        private const val GESTE = "geste"
        private const val LIAISON = "liaison"
        private const val EFFACEMENT = "effacement"
        private const val ADRESSE = "adresse"
        private const val RESIDENCE = "residence"
        private const val JETON = "jeton"
        private const val EXPIRE_LE = "expire_le"

        fun versDonnees(message: MessageLiaison): Map<String, String> = when (message) {
            is Liaison -> mapOf(
                GESTE to LIAISON, ADRESSE to message.adresse, RESIDENCE to message.residence,
                JETON to message.jeton, EXPIRE_LE to message.expireLe,
            )
            is Effacement -> mapOf(GESTE to EFFACEMENT, ADRESSE to message.adresse)
        }

        /** Un message incomplet ou inconnu ne se lit pas : `null`, jamais une liaison à moitié. */
        fun depuis(donnees: Map<String, String?>): MessageLiaison? {
            val adresse = donnees[ADRESSE]?.takeIf { it.isNotBlank() } ?: return null
            return when (donnees[GESTE]) {
                LIAISON -> Liaison(
                    adresse,
                    donnees[RESIDENCE]?.takeIf { it.isNotBlank() } ?: return null,
                    donnees[JETON]?.takeIf { it.isNotBlank() } ?: return null,
                    donnees[EXPIRE_LE]?.takeIf { it.isNotBlank() } ?: return null,
                )
                EFFACEMENT -> Effacement(adresse)
                else -> null
            }
        }
    }
}
