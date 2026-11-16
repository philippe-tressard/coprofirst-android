// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.notifications.push

import android.content.Context
import androidx.core.content.edit

/**
 * Ce que les notifications gardent sur l'appareil, et nulle part ailleurs : les abonnements
 * (résidence → identifiant opaque), la demande d'autorisation faite, la date d'un « Plus
 * tard ». Aucun jeton. Exclu de toute sauvegarde (`regles_sauvegarde.xml`).
 */
interface ReglagesPush {
    fun lire(cle: String): String?

    fun ecrire(cle: String, valeur: String?)

    fun toutes(): Map<String, String>
}

/** Les réglages dans les préférences privées de l'app (`push` ; la montre a les siennes, `montre`). */
class ReglagesPreferences(contexte: Context, fichier: String = "push") : ReglagesPush {
    private val preferences = contexte.getSharedPreferences(fichier, Context.MODE_PRIVATE)

    override fun lire(cle: String): String? = preferences.getString(cle, null)

    override fun ecrire(cle: String, valeur: String?) = preferences.edit {
        if (valeur == null) remove(cle) else putString(cle, valeur)
    }

    override fun toutes(): Map<String, String> =
        preferences.all.mapNotNull { (cle, valeur) -> (valeur as? String)?.let { cle to it } }.toMap()
}
