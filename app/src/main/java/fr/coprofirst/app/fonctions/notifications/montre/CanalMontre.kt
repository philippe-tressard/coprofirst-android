// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.notifications.montre

/**
 * Le chemin du téléphone vers la montre. Une interface : son seul transport réel passe par
 * Google Play services (`CanalWearable`, permis par la licence, #213) ; les tests le simulent.
 */
interface CanalMontre {
    /** Une montre joignable porte l'app CoproFirst (sa capacité déclarée). */
    suspend fun montreJointe(): Boolean

    suspend fun transmettre(message: MessageLiaison)
}
