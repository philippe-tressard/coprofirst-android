// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.session

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

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
 * Le coffre de l'app : chaque valeur chiffrée (AES-GCM) par une clé du Keystore d'Android,
 * qui ne quitte jamais l'appareil ; seul le chiffré va aux préférences privées — exclues de
 * toute sauvegarde (`regles_*.xml`). Sans biométrie : l'arrivée relit le courriel avant
 * toute requête, comme le Trousseau de l'iOS (`AfterFirstUnlockThisDeviceOnly`).
 */
class CoffreKeystore(contexte: Context) : CoffreDuCompte {
    private val preferences = contexte.getSharedPreferences(FICHIER, Context.MODE_PRIVATE)

    override fun lire(cle: String): String? {
        val texte = preferences.getString(cle, null) ?: return null
        // Une valeur illisible (clé perdue, réinstallation) s'oublie : rien n'est rendu faux.
        return runCatching {
            val octets = Base64.decode(texte, Base64.NO_WRAP)
            val chiffre = Cipher.getInstance(TRANSFORMATION)
            chiffre.init(Cipher.DECRYPT_MODE, cle(), GCMParameterSpec(128, octets, 0, TAILLE_IV))
            String(chiffre.doFinal(octets, TAILLE_IV, octets.size - TAILLE_IV), Charsets.UTF_8)
        }.onFailure { effacer(cle) }.getOrNull()
    }

    override fun ranger(valeur: String, cle: String): Boolean = runCatching {
        val chiffre = Cipher.getInstance(TRANSFORMATION)
        chiffre.init(Cipher.ENCRYPT_MODE, cle())
        val octets = chiffre.iv + chiffre.doFinal(valeur.toByteArray(Charsets.UTF_8))
        preferences.edit { putString(cle, Base64.encodeToString(octets, Base64.NO_WRAP)) }
    }.isSuccess

    override fun effacer(cle: String) = preferences.edit { remove(cle) }

    private fun cle(): SecretKey {
        val magasin = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (magasin.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generateur = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generateur.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generateur.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "fr.coprofirst.app.compte"
        const val FICHIER = "coffre-du-compte"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAILLE_IV = 12
    }
}
