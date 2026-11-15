// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.session

import kotlinx.serialization.Serializable

/**
 * Une installation de CoproFirst : une résidence, à son adresse. Ne garde que ce que
 * GET /config sert publiquement — rien qui dise qui est l'utilisateur.
 */
@Serializable
data class Installation(
    /** `https://<hôte>`, déjà normalisée (`AdresseInstallation`). */
    val url: String,
    /** Le nom de la résidence (`site_nom`). */
    val nom: String,
    /** Le contrat servi (`version_contrat`), tel que servi ; absent s'il ne l'est pas. */
    val versionContrat: String? = null,
    /** Le code de sa palette (`site_palette`) ; absent : la charte. */
    val codePalette: String? = null,
) {
    val adresse: AdresseInstallation? get() = AdresseInstallation.relire(url)
    val contrat: VersionContrat? get() = VersionContrat.lire(versionContrat)

    companion object {
        /**
         * L'installation que décrit GET /config.
         * @throws ErreurEntree.PasCoproFirst si rien n'y nomme une résidence ;
         * @throws ErreurEntree.ResidenceAMettreAJour si elle sert un contrat plus
         *   ancien que le plancher : ses réponses ne se décoderaient pas. Une version
         *   absente ou illisible ne bloque pas.
         */
        fun depuisConfig(adresse: AdresseInstallation, cles: Map<String, String?>): Installation {
            val nom = cles["site_nom"]?.trim().orEmpty()
            if (nom.isEmpty()) throw ErreurEntree.PasCoproFirst
            val installation = Installation(adresse.url, nom, cles["version_contrat"], cles["site_palette"]?.takeIf { it.isNotBlank() })
            val contrat = installation.contrat
            if (contrat != null && contrat < VersionContrat.PLANCHER) throw ErreurEntree.ResidenceAMettreAJour
            return installation
        }
    }
}
