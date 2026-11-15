// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.session

import java.net.URI

/**
 * L'adresse d'une installation, saisie ou scannée, ramenée à `https://<hôte>[:port]`.
 * Jamais en clair : l'app parle HTTPS, comme les installations. Aucun annuaire ne les
 * liste — l'app ne connaît que les adresses que l'utilisateur lui donne.
 */
@JvmInline
value class AdresseInstallation private constructor(val url: String) {
    /** L'hôte, tel qu'on le saisirait (et son port s'il en a un). */
    val enSaisie: String get() = url.removePrefix("https://")

    /** La racine de l'API servie par cette installation. */
    val baseApi: String get() = "$url/api"

    companion object {
        /** L'adresse de la démonstration de CoproFirst. */
        val DEMO: AdresseInstallation = requireNotNull(normaliser("demo.coprofirst.fr"))

        /** La saisie ramenée à `https://<hôte>`, ou `null` si elle n'en est pas une. */
        fun normaliser(saisie: String): AdresseInstallation? {
            var texte = saisie.trim().lowercase()
            if (texte.isEmpty() || texte.any { it.isWhitespace() }) return null
            texte = texte.removePrefix("http://")
            if (!texte.startsWith("https://")) texte = "https://$texte"
            val uri = runCatching { URI(texte) }.getOrNull() ?: return null
            val hote = uri.host ?: return null
            if (!hote.contains('.') || hote.startsWith('.') || hote.endsWith('.')) return null
            if (uri.rawUserInfo != null) return null
            val port = if (uri.port in 1..65535) ":${uri.port}" else ""
            return AdresseInstallation("https://$hote$port")
        }

        /** Relit une adresse déjà normalisée (stockage) ; `null` si elle ne l'est plus. */
        fun relire(url: String): AdresseInstallation? = normaliser(url)?.takeIf { it.url == url }
    }
}
