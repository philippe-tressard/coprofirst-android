// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun

import android.content.Context
import androidx.core.content.edit

/**
 * Ce que l'app garde sur l'appareil, et nulle part ailleurs, dans un fichier par usage :
 * la liaison avec la montre (`montre` : l'identifiant de la montre reliée, par résidence),
 * les notifications push (`push` : les abonnements opaques, la demande faite, un « Plus
 * tard »). Aucun jeton. Exclu de toute sauvegarde (`regles_sauvegarde.xml`).
 */
interface ReglagesAppareil {
    fun lire(cle: String): String?

    fun ecrire(cle: String, valeur: String?)

    fun toutes(): Map<String, String>
}

/** Les réglages dans les préférences privées de l'app (`fichier`). */
class ReglagesPreferences(contexte: Context, fichier: String) : ReglagesAppareil {
    private val preferences = contexte.getSharedPreferences(fichier, Context.MODE_PRIVATE)

    override fun lire(cle: String): String? = preferences.getString(cle, null)

    override fun ecrire(cle: String, valeur: String?) = preferences.edit {
        if (valeur == null) remove(cle) else putString(cle, valeur)
    }

    override fun toutes(): Map<String, String> =
        preferences.all.mapNotNull { (cle, valeur) -> (valeur as? String)?.let { cle to it } }.toMap()
}
