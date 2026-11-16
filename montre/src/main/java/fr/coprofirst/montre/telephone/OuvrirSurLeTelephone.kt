// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.montre.telephone

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.wear.remote.interactions.RemoteActivityHelper

/**
 * « Continuer sur le téléphone », comme Handoff sur l'Apple Watch : l'adresse s'ouvre sur le
 * téléphone relié, en lien d'application (#7). La montre ne la suit jamais elle-même.
 *
 * Passe par `RemoteActivityHelper` (AndroidX), qui tire `play-services-wearable` : permis par
 * la licence (point 4, décision de Philippe du 16/11/2026, #213).
 */
object OuvrirSurLeTelephone {
    fun ouvrir(contexte: Context, url: String) {
        val intention = Intent(Intent.ACTION_VIEW, url.toUri()).addCategory(Intent.CATEGORY_BROWSABLE)
        RemoteActivityHelper(contexte, ContextCompat.getMainExecutor(contexte)).startRemoteActivity(intention, null)
    }
}
