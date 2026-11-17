// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.montre.liaison

import fr.coprofirst.app.fonctions.notifications.montre.MessageLiaison
import org.junit.Assert.assertEquals
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Test

/** La capacité que la montre déclare (ressource) est celle que le téléphone cherche (constante). */
class CapaciteTest {
    @Test
    fun laCapaciteDeclareeEstCelleQueLeTelephoneCherche() {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/values/wear.xml"))
        val elements = document.getElementsByTagName("item")
        assertEquals(listOf(MessageLiaison.CAPACITE), (0 until elements.length).map { elements.item(it).textContent.trim() })
    }
}
