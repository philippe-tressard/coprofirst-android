// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.entree.vitrine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** La copie embarquée de `contenu.json` (scripts/importer-vitrine.sh) et sa mise en pages. */
class PagesVitrineTest {
    private val embarque = File("src/main/assets/vitrine/contenu.json").readText()

    @Test
    fun laCopieEmbarqueePorteChaqueIdDontLAppDepend() {
        val contenu = ContenuDuSite.lire(embarque)
        assertNotNull("contenu.json embarqué illisible", contenu)
        contenu!!
        ContenuVitrine.idsRequis.forEach { chemin ->
            val (section, element) = chemin.split('/').let { it[0] to it.getOrNull(1) }
            val bloc = contenu.section(section)
            assertNotNull("section $section", bloc)
            if (element != null) assertNotNull("élément $chemin", bloc!!.element(element))
        }
    }

    @Test
    fun laCopieEmbarqueeDonneDesPagesCompletes() {
        val pages = PagesVitrine.lire(embarque)!!
        assertEquals(ContenuVitrine.rangees, pages.enSavoirPlus.rangees.map { it.id })
        assertEquals(listOf("user-cs", "user-copro", "user-loc"), pages.demo.personnages.map { it.code })
        // Le chapeau est la PREMIÈRE phrase du texte du site.
        assertTrue(pages.enSavoirPlus.chapeau.endsWith("."))
        assertTrue(pages.demo.avertissement.isNotBlank())
    }

    /** Un contenu auquel manque un id requis ne donne pas de pages, jamais un écran à moitié vide. */
    @Test
    fun unIdRequisManquantNeDonnePasDePages() {
        val racine = Json.parseToJsonElement(embarque).jsonObject
        val sections = racine["sections"]!!.jsonArray.map { section ->
            val objet = section.jsonObject
            if (objet["id"]!!.jsonPrimitive.content != ContenuVitrine.DEMO) return@map objet
            JsonObject(objet + ("elements" to JsonArray(objet["elements"]!!.jsonArray.drop(1))))
        }
        assertNull(PagesVitrine.lire(JsonObject(racine + ("sections" to JsonArray(sections))).toString()))
    }

    @Test
    fun unFormatPlusRecentOuUnTexteIllisibleSeRefusent() {
        val racine = Json.parseToJsonElement(embarque).jsonObject
        assertNull(ContenuDuSite.lire(JsonObject(racine + ("format" to JsonPrimitive(2))).toString()))
        assertNull(PagesVitrine.lire("pas du JSON"))
    }

    @Test
    fun laPremierePhraseNeCoupePasUneQuestion() {
        assertEquals("Une phrase.", premierePhrase("Une phrase. Une autre."))
        assertEquals("« Où en est ma demande ? »", premierePhrase("« Où en est ma demande ? »"))
        assertEquals("Vraiment ?", premierePhrase("Vraiment ? Oui."))
        assertEquals("Sans fin", premierePhrase("Sans fin"))
    }
}
