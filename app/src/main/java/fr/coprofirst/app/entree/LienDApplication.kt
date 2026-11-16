// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.entree

import fr.coprofirst.app.session.AdresseInstallation
import java.net.URI

/**
 * Un lien d'application (#7) → la résidence qu'il désigne, comme `LienUniversel` de l'iOS.
 * Il ne fait confiance à rien : seul HTTPS passe, sans identifiants dans l'adresse, et
 * l'hôte est normalisé (`AdresseInstallation`). Qu'il vienne d'un hôte déclaré au manifeste
 * ne prouve rien de plus : l'adresse suit la découverte, puis la garde de l'hôte
 * (`OrigineAdresse.Lien`).
 *
 * Les chemins que l'app ouvre sont ceux du manifeste (`AndroidManifest.xml`), repris de
 * l'`apple-app-site-association` des installations ; les deux liens à jeton
 * (`/auth/verifier-email`, `/auth/reinitialisation-mdp`) n'y sont pas — ils restent au
 * navigateur. ⚠️ Divergence déclarée : l'iOS les ouvre dans Safari, au-dessus de l'app.
 */
object LienDApplication {
    /** L'adresse de la résidence du lien, ou `null` s'il n'en désigne pas une. */
    fun adresse(lien: String): AdresseInstallation? {
        val uri = runCatching { URI(lien.trim()) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.rawUserInfo != null) return null
        val hote = uri.host?.takeIf { it.isNotEmpty() } ?: return null
        val port = if (uri.port in 1..65535) ":${uri.port}" else ""
        return AdresseInstallation.normaliser(hote + port)
    }

    /** Le chemin du lien (`/tickets/12`), que la porte lit (`LienDuSite`) ; vide s'il n'en a pas. */
    fun chemin(lien: String): String = runCatching { URI(lien.trim()).path }.getOrNull().orEmpty()
}
