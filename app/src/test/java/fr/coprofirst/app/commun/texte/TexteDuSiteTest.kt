// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.texte

import androidx.compose.ui.text.AnnotatedString
import fr.coprofirst.app.commun.ConstantesDuServeur
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** La porte du texte riche du site : jamais une balise à l'écran — les cas de l'iOS (`TexteDuSiteTests`). */
class TexteDuSiteTest {
    private fun liens(texte: AnnotatedString) =
        texte.getLinkAnnotations(0, texte.length).map { (it.item as LinkAnnotation.Url).url }

    private fun styleDe(texte: AnnotatedString, morceau: String) =
        texte.spanStyles.first { texte.text.substring(it.start, it.end) == morceau }.item

    @Test
    fun lApercuRetireLesBalisesEtDecodeLesEntites() {
        val html = "<p>Le sol est <strong>glissant</strong>&nbsp;: attention.</p><p>L&#39;eau &amp; le gaz</p>"
        assertEquals("Le sol est glissant : attention. L'eau & le gaz", TexteDuSite.apercu(html))
    }

    @Test
    fun lApercuMetLesParagraphesEtLesSautsEnEspaces() {
        assertEquals("Un Deux Trois • Quatre", TexteDuSite.apercu("<p>Un</p><p>Deux<br>Trois</p><ul><li>Quatre</li></ul>"))
    }

    @Test
    fun unTexteSimpleSansBaliseResteTelQuel() {
        assertEquals("Pierre & Marie, 3 < 4", TexteDuSite.apercu("Pierre & Marie, 3 < 4"))
        assertEquals("Ligne un\nLigne deux", TexteDuSite.riche("Ligne un\nLigne deux").text)
    }

    @Test
    fun unHTMLVideNeDonneRien() {
        assertEquals("", TexteDuSite.apercu(""))
        assertEquals("", TexteDuSite.apercu("<p></p><p> </p>"))
        assertTrue(TexteDuSite.riche("<p><br></p>").isEmpty())
    }

    /** Une charge hostile : rien ne s'exécute, rien ne se charge, aucun code n'apparaît. */
    @Test
    fun uneChargeHostileSeReduitASonTexteSur() {
        val html = "<p>Bonjour</p><script>alert('x')</script><style>p{color:red}</style>" +
            "<img src=\"https://pirate.example/p.png\" onerror=\"alert(1)\"><iframe src=\"https://pirate.example\"></iframe>" +
            "<p onclick=\"alert(2)\">fin</p>"
        val apercu = TexteDuSite.apercu(html)
        assertEquals("Bonjour fin", apercu)
        for (interdit in listOf("alert", "script", "onerror", "pirate", "<", ">", "color")) {
            assertFalse("« $interdit » ne doit pas passer", interdit in apercu)
        }
        assertEquals("aucun lien ne naît d'une image ou d'un cadre", emptyList<String>(), liens(TexteDuSite.riche(html)))
    }

    @Test
    fun unLienJavascriptOuInconnuNEstPasTouchable() {
        val hostiles = listOf(
            "javascript:alert(1)", "JaVaScRiPt:alert(1)", "java\tscript:alert(1)", " javascript:alert(1)",
            "data:text/html,<b>x</b>", "file:///etc/passwd", "vbscript:x", "/relatif", "//hote/x", "tel:0102",
        )
        for (href in hostiles) {
            val riche = TexteDuSite.riche("<p><a href=\"$href\">clic</a></p>")
            assertEquals("$href ne doit pas être touchable", emptyList<String>(), liens(riche))
            assertEquals("le texte du lien reste", "clic", riche.text)
            assertEquals(null, TexteDuSite.lienSur(href))
        }
    }

    @Test
    fun unLienWebOuCourrielEstTouchable() {
        val riche = TexteDuSite.riche(
            "<p>Voir <a href=\"https://exemple.test/a?b=1&amp;c=2\">ici</a> ou <a href=\"mailto:cs@exemple.test\">écrire</a></p>",
        )
        assertEquals(listOf("https://exemple.test/a?b=1&c=2", "mailto:cs@exemple.test"), liens(riche))
        assertEquals("Voir ici ou écrire", riche.text)
        assertEquals("https://exemple.test", TexteDuSite.lienSur("  https://exemple.test  "))
        assertEquals(null, TexteDuSite.lienSur("https://"))
    }

    @Test
    fun leDetailGardeGrasItaliqueListesEtTitres() {
        val riche = TexteDuSite.riche(
            "<h2>Travaux</h2><p>Le <strong>lundi</strong> et le <em>mardi</em>.</p><ol><li>Un</li><li>Deux</li></ol>",
        )
        assertEquals("Travaux\n\nLe lundi et le mardi.\n\n1. Un\n2. Deux", riche.text)
        assertEquals(FontWeight.Bold, styleDe(riche, "lundi").fontWeight)
        assertEquals(FontStyle.Italic, styleDe(riche, "mardi").fontStyle)
        assertEquals(FontWeight.SemiBold, styleDe(riche, "Travaux").fontWeight)
    }

    /** Hors de la liste blanche du site (`BalisesDuSite`, générée) : le texte, sans la forme. */
    @Test
    fun uneBaliseHorsListeSeReduitASonTexte() {
        assertFalse("table" in BalisesDuSite.balises)
        assertEquals("a b", TexteDuSite.apercu("<table><tr><td>a</td><td>b</td></tr></table>"))
        assertEquals(setOf("http", "https", "mailto"), ConstantesDuServeur.schemasDeLien)
    }

    /** Une troncature du serveur (`contenu[:300]`) peut couper une balise en deux. */
    @Test
    fun uneBaliseCoupeeEnFinDeTexteDisparait() {
        assertEquals("Le plombier passe dem", TexteDuSite.apercu("<p>Le plombier passe <strong>dem"))
        assertEquals("Le plombier passe", TexteDuSite.apercu("<p>Le plombier passe <a href=\"https://exe"))
    }

    @Test
    fun unTresLongTexteSeConvertitEnEntier() {
        val html = "<p>Un paragraphe <strong>riche</strong> de la résidence.</p>".repeat(2_000)
        val apercu = TexteDuSite.apercu(html)
        assertFalse("<" in apercu)
        assertEquals(2_001, apercu.split("riche").size)
    }

    // Les listes de l'éditeur du site (#25 de l'iOS)

    /** La forme exacte de l'éditeur du site (Tiptap) : chaque élément est `<li><p>…</p></li>`. */
    @Test
    fun leMarqueurEtLeTexteRestentSurLaMemeLigne() {
        val html = "<p>Constat :</p><ol><li><p>le portillon ne s'ouvre pas ;</p></li>" +
            "<li><p>le badge clignote rouge.</p></li></ol>"
        val blocs = TexteDuSite.blocs(html)
        assertEquals(listOf(null, "1.", "2."), blocs.map { it.marqueur })
        assertEquals(
            listOf("Constat :", "le portillon ne s'ouvre pas ;", "le badge clignote rouge."),
            blocs.map { it.texte.text },
        )
        assertEquals(listOf(false, true, false), blocs.map { it.ecart })
        assertEquals("Constat :\n\n1. le portillon ne s'ouvre pas ;\n2. le badge clignote rouge.", TexteDuSite.riche(html).text)
        assertEquals("Constat : 1. le portillon ne s'ouvre pas ; 2. le badge clignote rouge.", TexteDuSite.apercu(html))
    }

    /** Deux `<p>` dans un même élément : le second passe à la ligne, sans marqueur, au même niveau. */
    @Test
    fun unSecondParagrapheDansUnElementSAligneSurLeTexte() {
        val blocs = TexteDuSite.blocs("<ul><li><p>Premier</p><p>suite du premier</p></li><li><p>Second</p></li></ul>")
        assertEquals(listOf("•", null, "•"), blocs.map { it.marqueur })
        assertEquals(listOf(1, 1, 1), blocs.map { it.niveau })
        assertEquals(listOf(false, false, false), blocs.map { it.ecart })
    }

    /** Une sous-liste vit dans le `<li>`, après son `<p>` : un niveau de plus. */
    @Test
    fun uneSousListeEstUnNiveauPlusLoin() {
        val blocs = TexteDuSite.blocs(
            "<ol><li><p>Bâtiment A</p><ul><li><p>hall</p></li><li><p>cave</p></li></ul></li><li><p>Bâtiment B</p></li></ol>",
        )
        assertEquals(listOf("1.", "•", "•", "2."), blocs.map { it.marqueur })
        assertEquals(listOf(1, 2, 2, 1), blocs.map { it.niveau })
        assertEquals("1. Bâtiment A\n    • hall\n    • cave\n2. Bâtiment B", TexteDuSite.assembler(blocs).text)
    }

    /** Un `<li>` sans `<p>` (HTML écrit à la main, ancien contenu) se lit pareil. */
    @Test
    fun unElementSansParagrapheGardeSonMarqueur() {
        assertEquals(listOf("•", "•"), TexteDuSite.blocs("<ul><li>un</li><li><strong>deux</strong></li></ul>").map { it.marqueur })
        assertEquals("• un\n• deux", TexteDuSite.riche("<ul><li>un</li><li>deux</li></ul>").text)
    }

    /** L'élément long de l'iOS : un seul bloc, le marqueur et tout le texte (le rendu, lui, est à l'écran). */
    @Test
    fun unElementLongResteUnSeulBloc() {
        val html = "<ol><li><p>" + "le portillon ne s'ouvre pas ; ".repeat(6) + "</p></li></ol>"
        assertEquals(1, TexteDuSite.blocs(html).size)
    }

    // Le chemin inverse : la saisie de l'app ↔ le HTML du site

    @Test
    fun unTexteSaisiSEcritCommeLEditeurDuSite() {
        assertEquals("<p>a &lt; b<br>c</p><p>d</p>", TexteDuSite.html(saisi = "a < b\nc\n\n  d  "))
    }

    @Test
    fun uneMiseEnFormeDuSiteNeSeReecritPas() {
        assertEquals("a < b\nc\n\nd", TexteDuSite.saisi("<p>a &lt; b<br>c</p><p>d</p>"))
        assertEquals("a\nb", TexteDuSite.saisi("<p>a<br/>b</p>"))
        assertEquals("Un texte d'avant l'éditeur.", TexteDuSite.saisi("Un texte d'avant l'éditeur."))
        for (riche in listOf("<p>Le sol est <strong>glissant</strong>.</p>", "<ul><li><p>a</p></li></ul>", "<p>a&nbsp;: b</p>")) {
            assertEquals("$riche perdrait sa mise en forme", null, TexteDuSite.saisi(riche))
        }
    }

    @Test
    fun lAllerRetourSaisieHtmlEstExact() {
        for (saisi in listOf("Une fuite.", "Une fuite.\n\nLe seau est posé.", "a < b & c > d\nligne")) {
            assertEquals(saisi, TexteDuSite.saisi(TexteDuSite.html(saisi = saisi)))
        }
    }
}
