// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.session

import org.junit.Assert.assertEquals
import org.junit.Test

class InstallationsConnuesTest {
    private class EnMemoire(var valeur: String? = null) : StockageResidences {
        override fun lire() = valeur
        override fun ecrire(valeur: String) { this.valeur = valeur }
    }

    private val a = Installation("https://a.exemple.test", "A", "1.27.0")
    private val b = Installation("https://b.exemple.test", "B")

    @Test
    fun laPlusRecenteEnTeteUneFoisParAdresse() {
        val connues = InstallationsConnues(EnMemoire())
        connues.retenir(a)
        connues.retenir(b)
        connues.retenir(a.copy(nom = "A renommée"))
        assertEquals(listOf("A renommée", "B"), connues.liste.map { it.nom })
    }

    @Test
    fun gardeesDUnLancementALAutre() {
        val stockage = EnMemoire()
        InstallationsConnues(stockage).apply { retenir(a); retenir(b) }
        assertEquals(listOf(b, a), InstallationsConnues(stockage).liste)
    }

    @Test
    fun stockageIllisibleOuAdresseInvalideIgnores() {
        assertEquals(emptyList<Installation>(), InstallationsConnues(EnMemoire("pas du json")).liste)
        val melange = """[{"url":"http://clair.exemple.test","nom":"X"},{"url":"https://a.exemple.test","nom":"A","inconnu":1}]"""
        assertEquals(listOf("A"), InstallationsConnues(EnMemoire(melange)).liste.map { it.nom })
    }
}
