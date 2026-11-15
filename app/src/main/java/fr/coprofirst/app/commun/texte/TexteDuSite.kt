// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.texte

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import java.net.URI
import java.net.URISyntaxException

/**
 * LA PORTE du texte riche du site, comme l'iOS (`TexteDuSite`) : descriptions
 * d'affaires, textes du fil, suites, sondages arrivent en HTML (l'éditeur du site). Aucun
 * écran ne les montre tels quels — `Text(html)` afficherait les balises —, et aucun ne
 * les convertit ailleurs qu'ici.
 *
 * - `apercu` : le texte simple d'une carte (balises retirées, entités décodées,
 *   paragraphes en espaces) ; la vue tronque ensuite.
 * - `blocs` : le texte riche d'un détail, bloc par bloc — un paragraphe, ou un élément de
 *   liste avec son marqueur (« • », « 2. ») et son niveau ; `TexteRiche` les met en page,
 *   retrait suspendu compris. Limité à la liste blanche du site (`BalisesDuSite`,
 *   générée) ; le reste se réduit à son texte.
 * - `riche` : les mêmes blocs en un seul `AnnotatedString` (marqueurs en tête de ligne),
 *   pour un texte qui ne peut être qu'un `Text`.
 *
 * Rien ne se charge : une image devient son texte de remplacement. Un lien n'est
 * touchable que s'il est absolu et de schéma `BalisesDuSite.schemasDeLien` (`lienSur`).
 * `styleLien` : la couleur des liens, posée par l'appelant (`TexteRiche` prend `primary`).
 */
object TexteDuSite {
    fun apercu(html: String): String =
        assembler(RenduHTML(html, riche = false).blocs).text
            .split('\n', ' ')
            .filter { it.isNotEmpty() }
            .joinToString(" ")

    fun blocs(html: String, styleLien: TextLinkStyles? = null): List<BlocDuSite> =
        RenduHTML(html, riche = true, styleLien = styleLien).blocs

    fun riche(html: String, styleLien: TextLinkStyles? = null): AnnotatedString = assembler(blocs(html, styleLien))

    /** Les blocs bout à bout : un saut de ligne entre deux, deux avant un paragraphe. */
    fun assembler(blocs: List<BlocDuSite>): AnnotatedString = buildAnnotatedString {
        blocs.forEachIndexed { rang, bloc ->
            if (rang > 0) append(if (bloc.ecart) "\n\n" else "\n")
            val retrait = "    ".repeat(maxOf(bloc.niveau - 1, 0))
            if (bloc.marqueur != null) append(retrait + bloc.marqueur + " ")
            append(bloc.texte)
        }
    }

    /**
     * Le sens inverse : un texte SAISI dans l'app, écrit comme l'éditeur du site l'écrit —
     * des paragraphes, des retours à la ligne, aucun caractère lu comme une balise. Une
     * description, une réponse, une suite : la même écriture partout.
     */
    fun html(saisi: String): String =
        saisi.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .split("\n\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("") { "<p>" + it.replace("\n", "<br>") + "</p>" }

    /**
     * Le texte à RÉÉCRIRE d'un HTML du site — seulement si le réenregistrer ne perd rien :
     * `html(saisi)` doit en refaire exactement le même HTML. Une mise en forme que l'app ne
     * sait pas écrire (gras, liste, lien, espace insécable) rend `null` : l'écran la montre
     * sans la toucher (`MISE_EN_FORME_AU_SITE`), plutôt que de l'effacer en silence. Un texte
     * sans balise (saisi avant l'éditeur) se réécrit tel quel.
     */
    fun saisi(html: String): String? {
        val texte = assembler(RenduHTML(html, riche = false).blocs).text
        if (html(texte) == html.replace("<br/>", "<br>")) return texte
        val brut = '<' !in html && '&' !in html
        return texte.takeIf { brut && it == html.trim() }
    }

    /**
     * Ce qu'une feuille dit sous un texte qu'elle montre sans le modifier (`saisi` rend
     * `null`) : la description d'une affaire, le texte d'une suite.
     */
    const val MISE_EN_FORME_AU_SITE = "Sa mise en forme (gras, listes, liens) se modifie depuis le site."

    /**
     * Un lien sûr : absolu, d'un schéma de la liste blanche, sans caractère de contrôle
     * (`java\tscript:` ne passe pas) — sinon `null`, et le texte reste inerte.
     *
     * ⚠️ Divergence déclarée : `java.net.URI` est plus strict que le `URL(string:)` de
     * l'iOS — une adresse avec un caractère interdit non encodé (une espace) reste inerte
     * ici, quand l'iOS l'encode et la rend touchable. Le côté sûr.
     */
    fun lienSur(href: String): String? {
        val propre = href.trim()
        if (propre.any { it.category == CharCategory.CONTROL }) return null
        val uri = try {
            URI(propre)
        } catch (_: URISyntaxException) {
            return null
        }
        val schema = uri.scheme?.lowercase() ?: return null
        if (schema !in BalisesDuSite.schemasDeLien) return null
        // Un hôte que `URI` ne range pas en nom de serveur (souligné, accent) se lit dans l'autorité.
        val hote = uri.host ?: uri.rawAuthority?.substringAfterLast('@')?.substringBefore(':')
        if (schema != "mailto" && hote.isNullOrEmpty()) return null
        return propre
    }
}

/**
 * Un bloc du texte riche (`BlocDuSite` de l'iOS) : un paragraphe (`niveau` 0), ou une
 * ligne d'un élément de liste — sa première porte le `marqueur`, les suivantes (un second
 * `<p>` dans le même `<li>`) n'en ont pas et s'alignent sur le texte.
 *
 * @property niveau la profondeur de liste : 0 hors liste, 1 pour une liste, 2 pour une sous-liste…
 * @property marqueur « • » ou « 2. » ; `null` pour un paragraphe ou la suite d'un élément.
 * @property ecart un écart de paragraphe le précède (sinon un simple saut de ligne).
 */
data class BlocDuSite(val niveau: Int, val marqueur: String?, val texte: AnnotatedString, val ecart: Boolean)
