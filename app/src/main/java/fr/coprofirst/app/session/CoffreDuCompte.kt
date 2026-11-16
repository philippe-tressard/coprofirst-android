// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.session

import android.content.Context
import android.util.Base64
import androidx.core.content.edit

/**
 * Ce que l'app retient d'un COMPTE, résidence par résidence — le courriel de la dernière
 * connexion réussie —, comme l'iOS (`CoffreDuCompte`) : jamais en clair sur l'appareil.
 */
interface CoffreDuCompte {
    fun lire(cle: String): String?

    /** `false` : le coffre a refusé — l'appelant n'efface rien d'autre. */
    fun ranger(valeur: String, cle: String): Boolean
    fun effacer(cle: String)
}

/** Un coffre qui s'oublie avec le processus : les tests et les aperçus. */
class CoffreEnMemoire : CoffreDuCompte {
    private val contenu = mutableMapOf<String, String>()

    @Synchronized
    override fun lire(cle: String) = contenu[cle]

    @Synchronized
    override fun ranger(valeur: String, cle: String): Boolean {
        contenu[cle] = valeur
        return true
    }

    @Synchronized
    override fun effacer(cle: String) {
        contenu.remove(cle)
    }
}

/**
 * Le coffre de l'app : chaque valeur chiffrée (`ChiffrementKeystore`, AES-GCM) par une clé
 * du Keystore d'Android, qui ne quitte jamais l'appareil ; seul le chiffré va aux
 * préférences privées — exclues de toute sauvegarde (`regles_*.xml`). Sans biométrie :
 * l'arrivée relit le courriel avant toute requête, comme le Trousseau de l'iOS
 * (`AfterFirstUnlockThisDeviceOnly`).
 */
class CoffreKeystore(
    contexte: Context,
    private val chiffrement: Chiffrement = ChiffrementKeystore("fr.coprofirst.app.compte"),
) : CoffreDuCompte {
    private val preferences = contexte.getSharedPreferences("coffre-du-compte", Context.MODE_PRIVATE)

    override fun lire(cle: String): String? {
        val texte = preferences.getString(cle, null) ?: return null
        // Une valeur illisible (clé perdue, réinstallation) s'oublie : rien n'est rendu faux.
        return runCatching { String(chiffrement.dechiffrer(Base64.decode(texte, Base64.NO_WRAP)), Charsets.UTF_8) }
            .onFailure { effacer(cle) }.getOrNull()
    }

    override fun ranger(valeur: String, cle: String): Boolean = runCatching {
        val octets = chiffrement.chiffrer(valeur.toByteArray(Charsets.UTF_8))
        preferences.edit { putString(cle, Base64.encodeToString(octets, Base64.NO_WRAP)) }
    }.isSuccess

    override fun effacer(cle: String) = preferences.edit { remove(cle) }
}
