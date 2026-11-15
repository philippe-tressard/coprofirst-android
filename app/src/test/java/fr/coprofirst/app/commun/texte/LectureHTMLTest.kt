// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.texte

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Le lecteur HTML maison et les entités — les cas de l'iOS, et le découpage lui-même. */
class LectureHTMLTest {
    @Test
    fun lesEntitesNumeriquesEtInconnuesSontSures() {
        assertEquals("’€��", Entites.decoder("&#x2019;&#8364;&#0;&#xD800;"))
        assertEquals("jamais avalée", "&inconnue; & &amp", Entites.decoder("&inconnue; & &amp"))
        assertEquals("�", Entites.decoder("&#x110000;"))
        assertEquals("😀 «a»", Entites.decoder("&#x1F600; &laquo;a&raquo;"))
    }

    @Test
    fun leDecoupageEnJetons() {
        assertEquals(
            listOf(
                JetonHTML.Ouvrante("p", emptyMap()),
                JetonHTML.Texte("a < b"),
                JetonHTML.Ouvrante("a", mapOf("href" to "https://x.fr/?a=1&b=2", "title" to "un > deux", "disabled" to "")),
                JetonHTML.Texte("lien"),
                JetonHTML.Fermante("a"),
                JetonHTML.Ouvrante("br", emptyMap()),
                JetonHTML.Fermante("p"),
            ),
            LectureHTML.jetons("<P>a < b<A HREF=\"https://x.fr/?a=1&amp;b=2\" title='un > deux' disabled>lien</a><br/></p>"),
        )
    }

    @Test
    fun commentairesEtDeclarationsDisparaissent() {
        assertEquals(
            listOf(JetonHTML.Texte("a"), JetonHTML.Texte("b")),
            LectureHTML.jetons("<!DOCTYPE html>a<!-- <p>caché</p> -->b<?xml x?>"),
        )
        assertEquals(listOf(JetonHTML.Texte("a")), LectureHTML.jetons("a<!-- jamais fermé"))
    }

    @Test
    fun uneValeurSansGuillemetsEtUnAttributEnDouble() {
        val jeton = LectureHTML.jetons("<img alt=Plan src=a.png ALT=autre>").single() as JetonHTML.Ouvrante
        assertEquals(mapOf("alt" to "Plan", "src" to "a.png"), jeton.attributs)
        assertTrue(LectureHTML.jetons("<p class=\"coupé").isEmpty())
    }

    @Test
    fun uneImageDevientSonTexteDeRemplacement() {
        assertEquals("Plan du parking", TexteDuSite.apercu("<p><img src=\"https://x.fr/p.png\" alt=\"Plan du parking\"></p>"))
    }
}
