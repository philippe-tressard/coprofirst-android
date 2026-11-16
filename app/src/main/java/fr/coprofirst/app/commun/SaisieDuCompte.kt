// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun

import androidx.compose.runtime.compositionLocalOf

/**
 * La saisie d'une affaire du compte, telle que le serveur la sert (`saisie_affaire_effective` :
 * `essentielle`, `complete`), posée une fois par la résidence ouverte et relue avec les droits
 * — l'`ouverteAJour.profil` de l'iOS. « Nouvelle affaire » et « Modifier » la lisent (#178) ;
 * `null` hors de la résidence, ou tant qu'elle n'est pas lue : tout reste à son rang.
 */
val LocalSaisieAffaire = compositionLocalOf<String?> { null }
