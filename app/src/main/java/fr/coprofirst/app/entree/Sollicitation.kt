// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.entree

import fr.coprofirst.app.navigation.Destination
import fr.coprofirst.app.navigation.LienDuSite
import fr.coprofirst.app.session.AdresseInstallation
import fr.coprofirst.app.session.Installation

/**
 * Ce qu'une sollicitation venue d'ailleurs demande à l'entrée : une résidence, et l'écran qui
 * y attend (`enAttente`). Deux sources, une porte (`ParcoursEntree.suivre`) :
 *
 * - un **lien d'application** (#7), comme `Parcours.suivre` de l'iOS : l'écran que le lien
 *   vise (une affaire, une page — `LienDuSite`, #147) ; `/auth/connexion` ne vise que la
 *   connexion. Un hôte jamais vu se découvre puis se confirme TOUJOURS avant le premier mot
 *   de passe (`OrigineAdresse.Lien`) ;
 * - une **notification push touchée** (#280), comme `NotificationsPush.touchee` de l'iOS : la
 *   cloche de SA résidence, retrouvée par l'abonnement. Jamais de découverte sur une
 *   notification : une résidence que l'appareil ne connaît plus n'ouvre rien.
 */
data class Sollicitation(val adresse: AdresseInstallation, val enAttente: LienEnAttente?) {
    companion object {
        fun deLien(lien: String): Sollicitation? {
            val adresse = LienDApplication.adresse(lien) ?: return null
            return Sollicitation(adresse, LienDuSite.destination(LienDApplication.chemin(lien))?.let { LienEnAttente(adresse, it) })
        }

        /** `residence` : l'adresse (`Installation.url`) que l'abonnement nomme ; `connues` : celles de l'appareil. */
        fun deNotification(residence: String, connues: List<Installation>): Sollicitation? {
            val adresse = AdresseInstallation.relire(residence)?.takeIf { a -> connues.any { it.adresse == a } } ?: return null
            return Sollicitation(adresse, LienEnAttente(adresse, Destination.Notifications))
        }
    }
}
