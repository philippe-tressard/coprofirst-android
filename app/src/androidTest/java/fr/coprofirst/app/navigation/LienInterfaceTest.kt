// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.coprofirst.app.ThemeCoproFirst
import fr.coprofirst.app.charte.Palettes
import fr.coprofirst.app.commun.GestesDeSortie
import fr.coprofirst.app.commun.attendreTexte
import fr.coprofirst.app.commun.residenceSimulee
import fr.coprofirst.app.entree.ServeurDeTest
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * La porte d'un lien d'application (#147) : l'écran visé, posé dans la résidence ouverte ;
 * une page que le compte ne voit pas revient au fil, comme l'iOS. Le lien se rend une fois posé.
 */
@RunWith(AndroidJUnit4::class)
class LienInterfaceTest {
    @get:Rule
    val ecran = createComposeRule()
    private val serveur = ServeurDeTest()
    private var lien by mutableStateOf<Destination?>(null)
    private var suivis = 0

    private val sortie = object : GestesDeSortie {
        override fun deconnecter() = Unit
        override fun oublierTout() = Unit
    }

    private fun ouvrir() {
        val session = serveur.session(residenceSimulee("https://residence.exemple.test", "Les Glycines"))
        ecran.setContent {
            ThemeCoproFirst(Palettes.pour(null), sombre = false) {
                EcranResidence(session, sortie, serveur.apparence(), lien) {
                    lien = null
                    suivis++
                }
            }
        }
        ecran.attendreTexte(PagesParDefaut.TITRE_DU_FIL)
    }

    @After
    fun fermer() = serveur.close()

    @Test
    fun uneAffaireViseeSOuvreDansLaResidenceOuverte() {
        ouvrir()
        ecran.runOnUiThread { lien = Destination.Element("affaire", "12") }
        ecran.attendreTexte("Affaire")
        ecran.waitForIdle()
        assertNull(lien)
        assertTrue(suivis == 1)
    }

    @Test
    fun unePageQueLeCompteNeVoitPasRevientAuFil() {
        ouvrir()
        ecran.runOnUiThread { lien = Destination.Page("admin") }
        ecran.waitUntil(DELAI_MS) { suivis == 1 }
        ecran.onNode(isSelectable() and hasText(PagesParDefaut.TITRE_DU_FIL)).assertIsSelected()
    }

    private companion object {
        const val DELAI_MS = 10_000L
    }
}
