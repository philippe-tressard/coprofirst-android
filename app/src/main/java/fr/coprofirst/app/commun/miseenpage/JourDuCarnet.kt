// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.miseenpage

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Le jour ouvert dans le CARNET (#242) — un pliable déplié en livre — : le Fil y montre une
 * journée par double page (`2026-11-17`, `null` : la plus récente). Fourni par la résidence, qui
 * le garde avec la page (rotation, pli) et dans le marque-page ; `null` hors du carnet : le Fil
 * se lit en entier, comme avant.
 */
val LocalJourDuCarnet = staticCompositionLocalOf<MutableState<String?>?> { null }
