// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.session

import android.app.KeyguardManager
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.annotation.RequiresApi
import androidx.core.content.edit
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.MGF1ParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec

/**
 * Les sessions gardées au Keystore d'Android (Android 11 et plus).
 *
 * Une paire RSA par hôte, née dans le Keystore : la clé privée n'en sort jamais, et ne
 * s'emploie qu'après la biométrie forte (invalidée si une empreinte ou un visage s'ajoute —
 * le `biometryCurrentSet` de l'iOS) ; sans biométrie inscrite, après le code de l'appareil ;
 * sans verrouillage, sans condition — et `garder` dit laquelle, pour que l'écran le dise.
 * Le jeton est chiffré (AES-GCM) par une clé à usage unique, elle-même chiffrée par la clé
 * PUBLIQUE : la rotation du jeton (`remplacer`) se chiffre sans rien demander, seule la
 * lecture déverrouille. Seul le chiffré va aux préférences privées, exclues de toute
 * sauvegarde ; le jeton n'est jamais écrit en clair ni journalisé.
 *
 * ⚠️ Divergence déclarée : sur Android 8 à 10, aucune session n'est gardée
 * (`SessionsGardees.Indisponibles`) — le chiffrement déverrouillé par le code de l'appareil
 * n'y existe pas ; la connexion se fait par le mot de passe, courriel pré-rempli.
 */
@RequiresApi(Build.VERSION_CODES.R)
class SessionsGardeesKeystore(private val contexte: Context) : SessionsGardees {
    private val preferences = contexte.getSharedPreferences(FICHIER, Context.MODE_PRIVATE)
    private val magasin: KeyStore get() = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    override val disponible = true

    override fun garder(hote: String, jeton: String): ProtectionSession? {
        supprimer(hote)
        for (protection in protectionsOffertes()) {
            val gardee = runCatching {
                genererPaire(alias(hote), protection)
                ecrire(hote, jeton, protection)
            }.isSuccess
            if (gardee && contient(hote)) return protection
            supprimer(hote)
        }
        return null
    }

    override fun contient(hote: String): Boolean =
        preferences.contains(hote) && runCatching { magasin.containsAlias(alias(hote)) }.getOrDefault(false)

    override fun remplacer(hote: String, jeton: String) {
        val protection = lireEntree(hote)?.protection ?: return
        // Un chiffrement qui échoue laisse l'ancien jeton : il est révoqué, la lecture le dira.
        runCatching { ecrire(hote, jeton, protection) }.onFailure { supprimer(hote) }
    }

    override suspend fun lire(hote: String, deverrouillage: Deverrouillage): LectureSession {
        val entree = lireEntree(hote) ?: return perdue(hote)
        val chiffre = try {
            val privee = magasin.getKey(alias(hote), null) as? PrivateKey ?: return perdue(hote)
            Cipher.getInstance(RSA).apply { init(Cipher.DECRYPT_MODE, privee, OAEP) }
        } catch (_: KeyPermanentlyInvalidatedException) {
            // La biométrie a changé : la session ne se rouvre plus que par le mot de passe.
            return perdue(hote)
        } catch (_: Exception) {
            return perdue(hote)
        }
        val ouvert = if (entree.protection == ProtectionSession.Aucune) {
            chiffre
        } else {
            deverrouillage.deverrouiller(chiffre, entree.protection) ?: return LectureSession.Annulee
        }
        return runCatching {
            val cle = SecretKeySpec(ouvert.doFinal(entree.cleChiffree), "AES")
            String(ChiffrementAesGcm { cle }.dechiffrer(entree.jetonChiffre), Charsets.UTF_8)
        }.fold({ LectureSession.Jeton(it) }, { perdue(hote) })
    }

    override fun supprimer(hote: String) {
        preferences.edit { remove(hote) }
        runCatching { magasin.deleteEntry(alias(hote)) }
    }

    override fun vider() {
        preferences.all.keys.forEach(::supprimer)
        preferences.edit { clear() }
    }

    private fun perdue(hote: String): LectureSession {
        supprimer(hote)
        return LectureSession.Perdue
    }

    /** Ce que l'appareil offre, du plus sûr au moins sûr — comme `Trousseau.enregistrer`. */
    private fun protectionsOffertes(): List<ProtectionSession> {
        val biometrie = contexte.getSystemService(BiometricManager::class.java)
            ?.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
        val verrouille = contexte.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true
        return buildList {
            if (biometrie) add(ProtectionSession.Biometrie)
            if (verrouille) add(ProtectionSession.CodeAppareil) else add(ProtectionSession.Aucune)
        }
    }

    private fun genererPaire(alias: String, protection: ProtectionSession) {
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_DECRYPT or KeyProperties.PURPOSE_ENCRYPT)
            .setKeySize(2048)
            .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA1)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
        when (protection) {
            ProtectionSession.Biometrie -> spec.setUserAuthenticationRequired(true)
                .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                .setInvalidatedByBiometricEnrollment(true)
            ProtectionSession.CodeAppareil -> spec.setUserAuthenticationRequired(true)
                .setUserAuthenticationParameters(0, KeyProperties.AUTH_DEVICE_CREDENTIAL)
            ProtectionSession.Aucune -> Unit
        }
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, KEYSTORE).apply { initialize(spec.build()) }
            .generateKeyPair()
    }

    /** Chiffre le jeton par une clé neuve, elle-même chiffrée par la clé publique de l'hôte. */
    private fun ecrire(hote: String, jeton: String, protection: ProtectionSession) {
        val publique = publique(hote) ?: error("clé absente")
        val cle = ByteArray(32).also(SecureRandom()::nextBytes)
        val jetonChiffre = ChiffrementAesGcm { SecretKeySpec(cle, "AES") }.chiffrer(jeton.toByteArray(Charsets.UTF_8))
        val cleChiffree = Cipher.getInstance(RSA).apply { init(Cipher.ENCRYPT_MODE, publique, OAEP) }.doFinal(cle)
        cle.fill(0)
        val valeur = listOf(protection.name, encoder(cleChiffree), encoder(jetonChiffre)).joinToString(SEPARATEUR)
        preferences.edit(commit = true) { putString(hote, valeur) }
    }

    /** La clé publique, recopiée hors du Keystore : chiffrer ne demande aucun déverrouillage. */
    private fun publique(hote: String): PublicKey? {
        val certificat = magasin.getCertificate(alias(hote)) ?: return null
        return KeyFactory.getInstance(KeyProperties.KEY_ALGORITHM_RSA)
            .generatePublic(X509EncodedKeySpec(certificat.publicKey.encoded))
    }

    private class Entree(val protection: ProtectionSession, val cleChiffree: ByteArray, val jetonChiffre: ByteArray)

    private fun lireEntree(hote: String): Entree? = runCatching {
        val (protection, cle, jeton) = requireNotNull(preferences.getString(hote, null)).split(SEPARATEUR)
        Entree(ProtectionSession.valueOf(protection), decoder(cle), decoder(jeton))
    }.getOrNull()

    private fun alias(hote: String) = "$PREFIXE_ALIAS$hote"
    private fun encoder(octets: ByteArray) = Base64.encodeToString(octets, Base64.NO_WRAP)
    private fun decoder(texte: String) = Base64.decode(texte, Base64.NO_WRAP)

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val FICHIER = "sessions-gardees"
        const val PREFIXE_ALIAS = "fr.coprofirst.app.session."
        const val SEPARATEUR = "|"
        const val RSA = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"

        /** MGF1 en SHA-1 : la seule que le Keystore accepte avec OAEP SHA-256, des deux côtés. */
        val OAEP = OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT)
    }
}
