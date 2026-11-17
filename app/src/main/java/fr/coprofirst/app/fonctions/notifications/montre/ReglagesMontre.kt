// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.notifications.montre

import android.content.Context
import androidx.core.content.edit

/**
 * Ce que la liaison avec la montre garde sur l'appareil, et nulle part ailleurs : l'identifiant
 * de la montre reliée, par résidence. Aucun jeton. Exclu de toute sauvegarde
 * (`regles_sauvegarde.xml`). Venu des notifications push, abandonnées le 17/11/2026 (#269).
 */
interface ReglagesMontre {
    fun lire(cle: String): String?

    fun ecrire(cle: String, valeur: String?)
}

/** Les réglages dans les préférences privées de l'app (`fichier`). */
class ReglagesPreferences(contexte: Context, fichier: String) : ReglagesMontre {
    private val preferences = contexte.getSharedPreferences(fichier, Context.MODE_PRIVATE)

    override fun lire(cle: String): String? = preferences.getString(cle, null)

    override fun ecrire(cle: String, valeur: String?) = preferences.edit {
        if (valeur == null) remove(cle) else putString(cle, valeur)
    }
}
