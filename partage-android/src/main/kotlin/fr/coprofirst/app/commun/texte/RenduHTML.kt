// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.texte

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em

/**
 * Le parcours des jetons de `LectureHTML` (`RenduHTML` de l'iOS) : un état de mise en
 * forme, des blocs qui s'ajoutent. Appelé par `TexteDuSite` seul.
 *
 * Les listes suivent la forme de l'éditeur du site (Tiptap) : chaque élément est
 * `<li><p>texte</p></li>`, une sous-liste vit dans le `<li>` après son `<p>`. Le premier
 * `<p>` d'un `<li>` ne coupe rien : le marqueur et le texte restent sur la même ligne
 * (#25 de l'iOS). Un second `<p>` du même `<li>` ouvre une ligne sans marqueur.
 *
 * `riche` : la mise en forme (gras, italique, souligné, barré, code, titres, liens) ;
 * sans elle, le texte seul (aperçu, saisie). `styleLien` : la couleur d'un lien, posée
 * par l'appelant (`TexteRiche`) — le rendu ne lit pas le thème.
 */
internal class RenduHTML(html: String, private val riche: Boolean, private val styleLien: TextLinkStyles? = null) {
    private class Liste(val ordonnee: Boolean) {
        var rang = 0
    }

    /** Le bloc en cours d'écriture ; `dernier` : son dernier caractère. */
    private class BlocEnCours(val niveau: Int, val marqueur: String?, val ecart: Boolean) {
        val texte = AnnotatedString.Builder()
        var dernier: Char? = null

        fun ajouter(morceau: String, style: SpanStyle?, lien: LinkAnnotation?) {
            val ecrire: AnnotatedString.Builder.() -> Unit = {
                if (style != null) withStyle(style) { append(morceau) } else append(morceau)
            }
            if (lien != null) texte.withLink(lien, ecrire) else texte.ecrire()
            dernier = morceau.lastOrNull() ?: dernier
        }

        fun fige() = BlocDuSite(niveau, marqueur, texte.toAnnotatedString(), ecart)
    }

    private val sortie = mutableListOf<BlocDuSite>()
    private var courant: BlocEnCours? = null
    private val ouvertes = mutableListOf<Pair<String, String?>>() // (balise, lien sûr d'un `a`)
    private val listes = mutableListOf<Liste>()
    private var coupure = 0 // 1 : ligne, 2 : paragraphe, dû avant le prochain texte
    private var marqueur: String? = null // le marqueur d'un `<li>` qui n'a pas encore de texte
    private var espace = false // un blanc dû avant le prochain texte
    private var ignore: String? = null // la balise dont on saute le contenu

    val blocs: List<BlocDuSite>

    init {
        if ('<' !in html) {
            // Un texte sans balise est du texte simple : ses retours à la ligne comptent.
            val texte = Entites.decoder(html).trim()
            if (texte.isNotEmpty()) sortie.add(BlocDuSite(0, null, AnnotatedString(texte), false))
        } else {
            LectureHTML.jetons(html).forEach(::lire)
            fermerBloc()
        }
        blocs = sortie.toList()
    }

    private fun lire(jeton: JetonHTML) {
        val ignoree = ignore
        if (ignoree != null) {
            if (jeton is JetonHTML.Fermante && jeton.nom == ignoree) ignore = null
            return
        }
        when (jeton) {
            is JetonHTML.Texte -> ecrire(jeton.texte)
            is JetonHTML.Ouvrante -> ouvrir(jeton.nom, jeton.attributs)
            is JetonHTML.Fermante -> fermer(jeton.nom)
        }
    }

    private fun ouvrir(nom: String, attributs: Map<String, String>) {
        if (!admise(nom)) return
        when (nom) {
            "br" -> retourALaLigne()
            "img" -> attributs["alt"]?.takeIf { it.isNotEmpty() }?.let(::ecrire)
            "hr" -> couper(2)
            "ul", "ol" -> {
                couper(if (listes.isEmpty()) 2 else 1)
                listes.add(Liste(ordonnee = nom == "ol"))
            }
            "li" -> ouvrirElementDeListe()
            else -> {
                // Le premier `<p>` d'un `<li>` : le marqueur attend son texte, rien ne coupe.
                if (marqueur == null) couperAutourDe(nom)
                ouvertes.add(nom to if (nom == "a") attributs["href"]?.let(TexteDuSite::lienSur) else null)
            }
        }
    }

    /** Un bloc coupe avant et après lui : un paragraphe (une ligne dans une liste), ou une ligne. */
    private fun couperAutourDe(nom: String) {
        if (nom in PARAGRAPHES) couper(if (listes.isEmpty()) 2 else 1)
        if (nom in LIGNES) couper(1)
    }

    private fun fermer(nom: String) {
        if (nom !in BalisesDuSite.balises) {
            espace = true
            return
        }
        when (nom) {
            "ul", "ol" -> {
                if (listes.isNotEmpty()) listes.removeAt(listes.lastIndex)
                couper(if (listes.isEmpty()) 2 else 1)
            }
            "li" -> {
                marqueur = null
                couper(1)
            }
            else -> {
                val rang = ouvertes.indexOfLast { it.first == nom }
                if (rang >= 0) ouvertes.subList(rang, ouvertes.size).clear()
                couperAutourDe(nom)
            }
        }
    }

    /**
     * Une balise de la liste blanche ; sinon, son contenu sauté (`script`…) ou réduit à son
     * texte, sans coller les mots.
     */
    private fun admise(nom: String): Boolean {
        if (nom in CONTENUS_IGNORES) {
            ignore = nom
            return false
        }
        if (nom !in BalisesDuSite.balises) {
            espace = true
            return false
        }
        return true
    }

    /** « • » ou « 2. » : il attend le premier texte de l'élément, sur la même ligne. */
    private fun ouvrirElementDeListe() {
        couper(1)
        listes.lastOrNull()?.let { it.rang++ }
        marqueur = listes.lastOrNull()?.let { if (it.ordonnee) "${it.rang}." else "•" } ?: "•"
    }

    private fun couper(n: Int) {
        fermerBloc()
        coupure = maxOf(coupure, n)
        espace = false
    }

    private fun fermerBloc() {
        courant?.takeIf { it.texte.length > 0 }?.let { sortie.add(it.fige()) }
        courant = null
    }

    /** `<br>` : un retour dans le bloc, jamais en tête. */
    private fun retourALaLigne() {
        val bloc = courant ?: return
        bloc.ajouter("\n", null, null)
        espace = false
    }

    /** Le texte, blancs réduits (sauf dans `pre`), dans le bloc courant ou un neuf. */
    private fun ecrire(brut: String) {
        var texte = brut
        var finBlanc = false
        if (ouvertes.none { it.first == "pre" }) {
            if (texte.firstOrNull()?.let(::blanc) == true) espace = true
            finBlanc = texte.lastOrNull()?.let(::blanc) == true
            texte = reduireBlancs(texte)
            if (texte.isEmpty()) return
        }
        val existant = courant
        val bloc = if (existant == null) {
            BlocEnCours(listes.size, if (listes.isEmpty()) null else marqueur, coupure >= 2 && sortie.isNotEmpty()).also {
                courant = it
                marqueur = null
                coupure = 0
            }
        } else {
            if (espace && existant.dernier?.isWhitespace() == false) existant.ajouter(" ", null, null)
            existant
        }
        if (riche) bloc.ajouter(texte, mise(), lien()) else bloc.ajouter(texte, null, null)
        espace = finBlanc
    }

    /** La mise en forme des balises ouvertes : gras, italique, souligné, barré, code, titres. */
    private fun mise(): SpanStyle? {
        val intentions = ouvertes.mapNotNull { INTENTIONS[it.first] }.toSet()
        val titre = ouvertes.lastOrNull { it.first in TITRES }?.let { TITRES.getValue(it.first) }
        val decorations = buildList {
            if (ouvertes.any { it.first == "u" || it.second != null }) add(TextDecoration.Underline)
            if (Intention.Barre in intentions) add(TextDecoration.LineThrough)
        }
        val style = SpanStyle(
            fontWeight = when {
                Intention.Gras in intentions -> FontWeight.Bold
                titre != null -> FontWeight.SemiBold
                else -> null
            },
            fontStyle = if (Intention.Italique in intentions) FontStyle.Italic else null,
            fontFamily = if (Intention.Code in intentions) FontFamily.Monospace else null,
            fontSize = titre ?: TextUnit.Unspecified,
            textDecoration = if (decorations.isEmpty()) null else TextDecoration.combine(decorations),
        )
        return style.takeIf { it != SpanStyle() }
    }

    /** Le lien sûr de la balise `a` ouverte la plus proche, s'il y en a un. */
    private fun lien(): LinkAnnotation? =
        ouvertes.lastOrNull { it.second != null }?.second?.let { LinkAnnotation.Url(it, styleLien) }

    private enum class Intention { Gras, Italique, Barre, Code }

    private companion object {
        /** Leur CONTENU n'est pas du texte : il disparaît avec la balise. */
        val CONTENUS_IGNORES = setOf(
            "script", "style", "template", "noscript", "iframe", "object", "embed", "svg", "math",
            "head", "title", "textarea", "select",
        )

        /** Les blocs : un écart de paragraphe avant et après (une ligne dans une liste). */
        val PARAGRAPHES = setOf("p", "blockquote", "pre", "h1", "h2", "h3", "h4", "h5", "h6", "hr", "details")

        /** Un simple retour à la ligne. */
        val LIGNES = setOf("div", "summary")

        val INTENTIONS = mapOf(
            "strong" to Intention.Gras, "b" to Intention.Gras, "summary" to Intention.Gras,
            "em" to Intention.Italique, "i" to Intention.Italique, "blockquote" to Intention.Italique,
            "s" to Intention.Barre, "code" to Intention.Code, "pre" to Intention.Code,
        )

        /**
         * La taille d'un titre, relative au texte de l'appelant : `h1`, `h2` un cran au-dessus
         * (le `title3` de l'iOS), les autres à sa taille (`headline`) — tous demi-gras.
         */
        val TITRES: Map<String, TextUnit> = mapOf(
            "h1" to 1.2.em, "h2" to 1.2.em,
            "h3" to TextUnit.Unspecified, "h4" to TextUnit.Unspecified,
            "h5" to TextUnit.Unspecified, "h6" to TextUnit.Unspecified,
        )

        /** Un blanc que le HTML réduit : espace, tabulation, fin de ligne (pas l'insécable). */
        fun blanc(c: Char) = c == ' ' || c == '\t' || c in "\n\r\u000B\u000C\u0085  "

        fun reduireBlancs(texte: String): String {
            val mots = StringBuilder(texte.length)
            var debut = -1
            for (i in 0..texte.length) {
                val estBlanc = i == texte.length || blanc(texte[i])
                if (!estBlanc && debut < 0) debut = i
                if (estBlanc && debut >= 0) {
                    if (mots.isNotEmpty()) mots.append(' ')
                    mots.append(texte, debut, i)
                    debut = -1
                }
            }
            return mots.toString()
        }
    }
}
