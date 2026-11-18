// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.navigation

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.coprofirst.app.ThemeCoproFirst
import fr.coprofirst.app.charte.Palettes
import fr.coprofirst.app.commun.GestesDeSortie
import fr.coprofirst.app.commun.ENTETE_SUSPENDUE
import fr.coprofirst.app.commun.REPERE_BANDEAU_SUSPENDUE
import fr.coprofirst.app.commun.attendreDisparition
import fr.coprofirst.app.commun.attendreTag
import fr.coprofirst.app.commun.attendreTexte
import fr.coprofirst.app.entree.ServeurDeTest
import fr.coprofirst.app.session.Installation
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * #233 (Registre R6) : une installation suspendue le dit sur chaque page de la résidence, comme le
 * `BandeauInstallation` du site ; hors suspension, aucun bandeau.
 */
@RunWith(AndroidJUnit4::class)
class BandeauInstallationInterfaceTest {
    @get:Rule
    val ecran = createComposeRule()
    private val serveur = ServeurDeTest()

    private val sortie = object : GestesDeSortie {
        override fun deconnecter() = Unit
        override fun oublierTout() = Unit
    }

    private fun ouvrir(suspendue: Boolean) {
        val session = serveur.session(Installation("https://residence.exemple.test", "Les Glycines", suspendue = suspendue))
        ecran.setContent {
            ThemeCoproFirst(Palettes.pour(null), sombre = false) { EcranResidence(session, sortie, serveur.apparence()) }
        }
        ecran.attendreTexte(PagesParDefaut.TITRE_DU_FIL)
    }

    @After
    fun fermer() = serveur.close()

    @Test
    fun uneInstallationSuspendueLeDitSurLaPage() {
        ouvrir(suspendue = true)
        ecran.attendreTag(REPERE_BANDEAU_SUSPENDUE)
        // Le libellé PUBLIÉ par la release (#288), composé comme le site.
        ecran.attendreTexte(ENTETE_SUSPENDUE)
        ecran.ouvrirLaPageDePlus("profil")
        ecran.attendreTag(REPERE_BANDEAU_SUSPENDUE)
    }

    @Test
    fun horsSuspensionAucunBandeau() {
        ouvrir(suspendue = false)
        ecran.attendreDisparition(hasTestTag(REPERE_BANDEAU_SUSPENDUE))
    }
}
