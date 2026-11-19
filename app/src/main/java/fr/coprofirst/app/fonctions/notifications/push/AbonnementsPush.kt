// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.notifications.push

import fr.coprofirst.app.commun.ReglagesAppareil
import java.security.SecureRandom
import java.util.Base64

/**
 * Les abonnements de cet appareil, comme l'iOS (`AbonnementsPush`) : un identifiant
 * ALÉATOIRE par résidence, tiré par l'app à l'enregistrement (spec push §5). C'est tout ce
 * que porte une notification : il ne signifie rien hors du téléphone, qui seul sait à quelle
 * résidence il répond. Gardés sur l'appareil, jamais ailleurs ; oubliés à la déconnexion.
 *
 * Une résidence se nomme par son adresse (`Installation.url`).
 */
class AbonnementsPush(private val reglages: ReglagesAppareil) {
    /** L'abonnement de cette résidence, tiré au premier enregistrement. */
    fun abonnement(residence: String): String =
        existant(residence) ?: tirer().also { reglages.ecrire(PREFIXE + residence, it) }

    fun existant(residence: String): String? = reglages.lire(PREFIXE + residence)

    /** La résidence qui répond à cet abonnement — `null` s'il n'est pas (ou plus) de cet appareil. */
    fun residence(abonnement: String): String? =
        reglages.toutes().entries.firstOrNull { it.key.startsWith(PREFIXE) && it.value == abonnement }?.key?.removePrefix(PREFIXE)

    fun oublier(residence: String) = reglages.ecrire(PREFIXE + residence, null)

    companion object {
        private const val PREFIXE = "abonnement:"
        private val alea = SecureRandom()

        /**
         * 24 octets du générateur cryptographique, en base64 « URL » sans remplissage :
         * 32 caractères de `[A-Za-z0-9_-]`, dans le motif du contrat
         * (`AppareilEnregistre.abonnement`, 16 à 64).
         */
        fun tirer(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(24).also(alea::nextBytes))
    }
}
