// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.notifications.push

/**
 * Le jeton que le service de notifications donne à l'app : un identifiant d'une personne
 * (spec push de coprofirst, N8), comme `JetonAPNs` de l'iOS. Il ne part que dans le corps
 * de POST /appareils et ne s'écrit JAMAIS — ni dans un journal, ni sur le disque, ni dans
 * un message d'erreur : sa forme écrite est masquée. Le système le redonne à chaque
 * inscription.
 */
class JetonPush(val valeur: String) {
    override fun toString(): String = MASQUE

    override fun equals(other: Any?): Boolean = other is JetonPush && other.valeur == valeur

    override fun hashCode(): Int = valeur.hashCode()

    companion object {
        const val MASQUE = "‹jeton masqué›"
    }
}
