// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.espacecs

import fr.coprofirst.app.navigation.Destination
import fr.coprofirst.app.navigation.RegistreDesMenus

/** La nature d'une affaire pour la porte (celle du fil et de `RegistreDesMenus.fiches`). */
private const val AFFAIRE = "affaire"

/**
 * Ouvrir une affaire depuis l'Espace CS (Courriels affaires, Reporting), par la porte du
 * menu (#81), comme le `NavigationLink` vers `DetailAffaireView` de l'iOS. `null` tant
 * qu'aucun menu ne déclare la fiche d'une affaire (Mes affaires, #16) : l'écran montre
 * alors le numéro sans lien, plutôt qu'un lien qui ne mène nulle part.
 */
internal fun lienAffaire(ouvrir: (Destination) -> Boolean, id: Int): (() -> Unit)? =
    if (AFFAIRE in RegistreDesMenus.fiches) ({ ouvrir(Destination.Element(AFFAIRE, id.toString())) }) else null
