// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.session

import fr.coprofirst.app.navigation.OrdrePages
import fr.coprofirst.app.navigation.PageDeNavigation
import fr.coprofirst.app.navigation.PagesParDefaut
import fr.coprofirst.app.navigation.SurchargePage
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
    /** L'ordre de la barre réglé par l'administration (`pages_order`) ; absent : l'ordre du site. */
    val ordrePages: List<String>? = null,
    /** Ce que l'administration a écrit pour chaque page (`page_config_<id>`). */
    val surchargesPages: Map<String, SurchargePage> = emptyMap(),
) {
    val adresse: AdresseInstallation? get() = AdresseInstallation.relire(url)
    val contrat: VersionContrat? get() = VersionContrat.lire(versionContrat)

    /** La barre de cette résidence : ses pages, dans son ordre, avec ses libellés. */
    val pages: List<PageDeNavigation>
        get() = OrdrePages.ordonner(PagesParDefaut.toutes, ordrePages, surchargesPages)

    companion object {
        private const val PREFIXE_PAGE = "page_config_"

        /**
         * L'installation que décrit GET /config.
         * @throws ErreurService.PasCoproFirst si rien n'y nomme une résidence ;
         * @throws ErreurService.ResidenceAMettreAJour si elle sert un contrat plus
         *   ancien que le plancher : ses réponses ne se décoderaient pas. Une version
         *   absente ou illisible ne bloque pas.
         */
        fun depuisConfig(adresse: AdresseInstallation, cles: Map<String, String?>): Installation {
            val nom = cles["site_nom"]?.trim().orEmpty()
            if (nom.isEmpty()) throw ErreurService.PasCoproFirst
            val surcharges = cles.entries
                .filter { it.key.startsWith(PREFIXE_PAGE) }
                .mapNotNull { (cle, json) -> SurchargePage.lire(json)?.let { cle.removePrefix(PREFIXE_PAGE) to it } }
                .toMap()
            val installation = Installation(
                url = adresse.url,
                nom = nom,
                versionContrat = cles["version_contrat"],
                codePalette = cles["site_palette"]?.takeIf { it.isNotBlank() },
                ordrePages = OrdrePages.lire(cles["pages_order"]),
                surchargesPages = surcharges,
            )
            val contrat = installation.contrat
            if (contrat != null && contrat < VersionContrat.PLANCHER) throw ErreurService.ResidenceAMettreAJour
            return installation
        }
    }
}
