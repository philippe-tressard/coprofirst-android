// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.magasin

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.coprofirst.app.commun.attendreQue
import fr.coprofirst.app.commun.attendreTexte
import fr.coprofirst.app.commun.toucherTag
import fr.coprofirst.app.commun.toucherTexte
import fr.coprofirst.app.entree.NavigationEntree
import fr.coprofirst.app.entree.ServeurDeTest
import fr.coprofirst.app.entree.entrerDansLaResidence
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Le parcours des écrans principaux EN SOMBRE et AU PLUS GRAND TEXTE d'Android (200 %),
 * exigé avant chaque soumission au magasin (#4, contrôle de cohérence maximale). Il passe
 * par l'entrée, le fil, « Mes lots & accès » et le profil d'une résidence fictive. Chaque
 * écran doit s'ouvrir et montrer ce qu'il porte : un écran qui casse au grand texte, ou
 * un contrôle masqué hors d'atteinte, fait échouer le test.
 *
 * La CI le joue sur chaque commit (job Interface). `scripts/controle_avant_soumission.py`
 * exige cette CI complète et verte sur le commit exact soumis.
 */
@RunWith(AndroidJUnit4::class)
class ParcoursSoumissionInterfaceTest {
    @get:Rule
    val ecran = createComposeRule()
    private val serveur = ServeurDeTest()

    @After
    fun fermer() = serveur.close()

    @Test
    fun lesEcransPrincipauxEnSombreEtAuPlusGrandTexte() {
        serveur.serveur.dispatcher = ResidenceFictive()
        val apparence = serveur.apparence().also { it.choisir("sombre") }
        ecran.setContent {
            val densite = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(densite.density, fontScale = PLUS_GRAND_TEXTE)) {
                NavigationEntree(apparence, serveur.parcours())
            }
        }
        ecran.entrerDansLaResidence(ResidenceFictive.ADRESSE, ResidenceFictive.NOM)
        // Une carte du fil se lit d'un bloc : son texte est dans sa description.
        ecran.attendreQue("la carte « Fuite d'eau » du fil") {
            ecran.onAllNodes(hasContentDescription("Fuite d'eau", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        ecran.toucherTexte("Accès")
        ecran.attendreTexte("T3")
        ecran.toucherTexte("Plus")
        ecran.toucherTag("plus.profil")
        ecran.attendreTexte("Camille EXEMPLE")
        assertTrue("« Sombre » choisi : sombre même sur un appareil réglé en clair", apparence.sombre(apparence.choix.value, systemeSombre = false))
    }

    private companion object {
        /** Le plus grand texte que propose Android. */
        const val PLUS_GRAND_TEXTE = 2f
    }
}
