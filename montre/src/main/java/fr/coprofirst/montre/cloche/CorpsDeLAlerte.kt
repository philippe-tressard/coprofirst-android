// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.montre.cloche

import androidx.compose.ui.text.AnnotatedString
import fr.coprofirst.app.commun.texte.TexteDuSite

/**
 * Le corps d'une alerte sur la montre : le HTML de l'éditeur du site, rendu par le lecteur du
 * téléphone (`TexteDuSite`, partagé, #206), jamais par un second. Ses liens ne se touchent
 * PAS : la montre ne suit aucun lien elle-même, elle passe la main au téléphone
 * (« Continuer sur le téléphone »). Le texte et ses styles (gras, italique…) restent.
 */
object CorpsDeLAlerte {
    fun de(html: String): AnnotatedString {
        val riche = TexteDuSite.riche(html)
        return AnnotatedString(riche.text, riche.spanStyles, riche.paragraphStyles)
    }
}
