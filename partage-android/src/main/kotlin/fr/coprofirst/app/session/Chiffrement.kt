// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.session

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Chiffrer ce que l'app écrit sur l'appareil : le coffre du compte, les lectures gardées. */
interface Chiffrement {
    fun chiffrer(clair: ByteArray): ByteArray

    /** @throws Exception si le chiffré est illisible (clé perdue, altéré). */
    fun dechiffrer(chiffre: ByteArray): ByteArray
}

/**
 * AES-GCM par une clé du Keystore d'Android, née sur l'appareil et qui n'en sort jamais —
 * sans biométrie : ce qu'elle protège se relit sans invite. Le chiffré porte son IV.
 */
open class ChiffrementAesGcm(private val cle: () -> SecretKey) : Chiffrement {
    override fun chiffrer(clair: ByteArray): ByteArray {
        val aes = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, cle()) }
        return aes.iv + aes.doFinal(clair)
    }

    override fun dechiffrer(chiffre: ByteArray): ByteArray {
        val aes = Cipher.getInstance(TRANSFORMATION)
        aes.init(Cipher.DECRYPT_MODE, cle(), GCMParameterSpec(128, chiffre, 0, TAILLE_IV))
        return aes.doFinal(chiffre, TAILLE_IV, chiffre.size - TAILLE_IV)
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAILLE_IV = 12
    }
}

/** La clé AES de ce nom au Keystore, créée à son premier emploi. */
class ChiffrementKeystore(alias: String) : ChiffrementAesGcm({ cleKeystore(alias) })

private fun cleKeystore(alias: String): SecretKey {
    val magasin = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    (magasin.getKey(alias, null) as? SecretKey)?.let { return it }
    val generateur = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
    generateur.init(
        KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build(),
    )
    return generateur.generateKey()
}
