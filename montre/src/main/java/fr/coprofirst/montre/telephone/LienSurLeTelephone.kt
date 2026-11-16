// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.montre.telephone

import java.net.URI
import java.net.URISyntaxException

/**
 * L'adresse qu'ouvre « Continuer sur le téléphone » : le `lien` de l'alerte (un chemin du
 * site, « /tickets/12 ») sous l'adresse de la résidence que le téléphone a donnée à
 * l'appairage. Le téléphone la reçoit comme un lien d'application (#7) : l'app ouvre l'écran
 * qu'elle vise, ou le navigateur à défaut.
 *
 * 🔴 Le lien vient du serveur : il ne peut viser QUE la résidence. Un chemin qui n'en est pas
 * un (« //autre.test », « https://… », « \\ ») ou qui change d'hôte ne s'ouvre pas.
 */
object LienSurLeTelephone {
    fun url(adresse: String, lien: String?): String? {
        if (lien == null || !lien.startsWith("/") || lien.startsWith("//") || '\\' in lien) return null
        return try {
            val base = URI(adresse)
            val cible = URI(adresse.trimEnd('/') + lien)
            cible.toString().takeIf { base.scheme == "https" && cible.scheme == "https" && cible.host == base.host && cible.port == base.port }
        } catch (_: URISyntaxException) {
            null
        }
    }
}
