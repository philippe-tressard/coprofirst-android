// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

/** La porte (#85), comme l'aiguillage de l'iOS : où se pose chaque destination. */
class PorteTest {
    private val barre = listOf("tableau-de-bord", "mes-demandes", "residence", "mon-lot")
    private val plus = listOf("communaute", "faq")

    private fun aiguiller(destination: Destination, fiches: Set<String> = setOf("affaire"), cloche: Boolean = true) =
        Aiguillage.aiguiller(destination, barre, plus, fiches, cloche)

    @Test
    fun pageDeLaBarreOuvreSonOnglet() {
        assertEquals(Aiguillage.Onglet("mes-demandes"), aiguiller(Destination.Page("mes-demandes")))
    }

    @Test
    fun pageDePlusSOuvreDansPlus() {
        assertEquals(Aiguillage.PageDePlus("faq"), aiguiller(Destination.Page("faq")))
        // Les entrées fixes de « Plus » aussi (Profil, À propos).
        assertEquals(Aiguillage.PageDePlus("profil"), aiguiller(Destination.Page(PagesDePlus.profil.id)))
    }

    @Test
    fun pageQueLeCompteNeVoitPasNeMeneNullePart() {
        assertEquals(Aiguillage.Aucun, aiguiller(Destination.Page("admin")))
    }

    @Test
    fun elementDeclareOuvreSaFiche() {
        val affaire = Destination.Element("affaire", "45")
        assertEquals(Aiguillage.Fiche(affaire), aiguiller(affaire))
    }

    @Test
    fun elementSansFicheDeclareeNeMeneNullePart() {
        assertEquals(Aiguillage.Aucun, aiguiller(Destination.Element("sondage", "7")))
    }

    @Test
    fun clocheSeulementSiLaPageExiste() {
        assertEquals(Aiguillage.Cloche, aiguiller(Destination.Notifications))
        assertEquals(Aiguillage.Aucun, aiguiller(Destination.Notifications, cloche = false))
    }

    @Test
    fun chaqueFicheDuRegistreEstUneNatureDuFil() {
        // Les natures sont celles du fil (CibleDuFil) : une faute de frappe au registre ne
        // s'ouvrirait jamais. La liste grandit avec les menus qui déclarent leurs fiches.
        val natures = setOf("affaire", "sondage")
        assertEquals(emptySet<String>(), RegistreDesMenus.fiches.keys - natures)
    }
}
