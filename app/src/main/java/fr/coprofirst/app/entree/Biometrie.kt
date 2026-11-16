// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.entree

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import fr.coprofirst.app.R
import fr.coprofirst.app.session.Deverrouillage
import fr.coprofirst.app.session.Installation
import fr.coprofirst.app.session.ProtectionSession
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.crypto.Cipher
import kotlin.coroutines.resume

/**
 * Ce que l'appareil offre pour rouvrir une session gardée, comme l'iOS (`Biometrie`) : le
 * libellé du bouton et le nom à dire. Android ne nomme pas sa biométrie : une empreinte
 * seule se dit « votre empreinte », tout autre capteur « la biométrie ». Sans biométrie
 * forte : « Ouvrir mon espace » (le code de l'appareil, ou rien, la protège).
 */
enum class Biometrie(@param:StringRes val ouvrir: Int, @param:StringRes val nom: Int?, @param:StringRes val question: Int) {
    Empreinte(R.string.retour_ouvrir_empreinte, R.string.biometrie_nom_empreinte, R.string.garde_titre_empreinte),
    Generique(R.string.retour_ouvrir_biometrie, R.string.biometrie_nom_generique, R.string.garde_titre_biometrie),
    Aucune(R.string.retour_ouvrir_espace, null, R.string.garde_titre_sans),
    ;

    companion object {
        fun actuelle(contexte: Context): Biometrie {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return Aucune
            val forte = contexte.getSystemService(BiometricManager::class.java)
                ?.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
            if (!forte) return Aucune
            val pm = contexte.packageManager
            val autre = pm.hasSystemFeature(PackageManager.FEATURE_FACE) || pm.hasSystemFeature(PackageManager.FEATURE_IRIS)
            return if (pm.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT) && !autre) Empreinte else Generique
        }
    }
}

@Composable
fun rememberBiometrie(): Biometrie {
    val contexte = LocalContext.current
    return remember(contexte) { Biometrie.actuelle(contexte) }
}

/**
 * La demande de déverrouillage, par l'invite du système (`BiometricPrompt`) : la biométrie
 * forte seule pour une session qu'elle protège — « Utiliser le mot de passe » y renonce —,
 * le code de l'appareil sinon. `null` avant Android 11 : rien n'y est gardé.
 */
@Composable
fun rememberDeverrouillage(residence: Installation?): Deverrouillage? {
    val contexte = LocalContext.current
    val titre = stringResource(R.string.deverrouiller_titre, residence?.nom.orEmpty())
    val renoncer = stringResource(R.string.retour_mot_de_passe)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
    val sousTitre = residence?.adresse?.enSaisie.orEmpty()
    return remember(contexte, titre, sousTitre, renoncer) {
        Deverrouillage { chiffre, protection -> demander(contexte, chiffre, protection, titre, sousTitre, renoncer) }
    }
}

@RequiresApi(Build.VERSION_CODES.R)
private suspend fun demander(
    contexte: Context,
    chiffre: Cipher,
    protection: ProtectionSession,
    titre: String,
    sousTitre: String,
    renoncer: String,
): Cipher? = suspendCancellableCoroutine { suite ->
    val executeur = contexte.mainExecutor
    val constructeur = BiometricPrompt.Builder(contexte).setTitle(titre)
    if (sousTitre.isNotEmpty()) constructeur.setSubtitle(sousTitre)
    if (protection == ProtectionSession.Biometrie) {
        constructeur.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setNegativeButton(renoncer, executeur) { _, _ -> if (suite.isActive) suite.resume(null) }
    } else {
        constructeur.setAllowedAuthenticators(BiometricManager.Authenticators.DEVICE_CREDENTIAL)
    }
    val annulation = CancellationSignal()
    suite.invokeOnCancellation { annulation.cancel() }
    constructeur.build().authenticate(
        BiometricPrompt.CryptoObject(chiffre),
        annulation,
        executeur,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(resultat: BiometricPrompt.AuthenticationResult) {
                if (suite.isActive) suite.resume(resultat.cryptoObject?.cipher)
            }

            // Annulée, refusée trop de fois, capteur indisponible : la session reste gardée.
            override fun onAuthenticationError(code: Int, message: CharSequence?) {
                if (suite.isActive) suite.resume(null)
            }
        },
    )
}
