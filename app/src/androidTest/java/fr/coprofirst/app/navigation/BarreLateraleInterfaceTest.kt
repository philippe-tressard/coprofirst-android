// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.coprofirst.app.ThemeCoproFirst
import fr.coprofirst.app.charte.Palettes
import fr.coprofirst.app.commun.GestesDeSortie
import fr.coprofirst.app.commun.attendre
import fr.coprofirst.app.commun.attendreDisparition
import fr.coprofirst.app.commun.attendreTag
import fr.coprofirst.app.commun.attendreTexte
import fr.coprofirst.app.commun.residenceSimulee
import fr.coprofirst.app.commun.toucherTag
import fr.coprofirst.app.entree.ServeurDeTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * #69, comme `BarreLateraleUITests` de l'iOS (#221) : une fenêtre large porte la barre latérale
 * (toutes les pages, puis Profil et À propos) à la place de la barre du bas ; une page de
 * « Plus » s'y ouvre sans retour ; repasser en fenêtre étroite garde la page ouverte.
 * La largeur est IMPOSÉE au cadre : le test se joue sur l'émulateur du téléphone.
 */
@RunWith(AndroidJUnit4::class)
class BarreLateraleInterfaceTest {
    @get:Rule
    val ecran = createComposeRule()
    private val serveur = ServeurDeTest()
    private var largeur by mutableStateOf(LARGE)

    private val sortie = object : GestesDeSortie {
        override fun deconnecter() = Unit
        override fun oublierTout() = Unit
    }

    private fun ouvrir(debut: Dp) {
        largeur = debut
        val session = serveur.session(residenceSimulee("https://residence.exemple.test", "Les Glycines"))
        ecran.setContent {
            ThemeCoproFirst(Palettes.pour(null), sombre = false) {
                // Calé en haut à gauche : plus large que l'écran du téléphone, le cadre en déborde à droite,
                // jamais à gauche — la barre latérale reste à l'écran.
                Box(Modifier.wrapContentSize(Alignment.TopStart, unbounded = true).requiredSize(largeur, 640.dp)) {
                    EcranResidence(session, sortie, serveur.apparence())
                }
            }
        }
        ecran.attendreTexte(PagesParDefaut.TITRE_DU_FIL)
    }

    @After
    fun fermer() = serveur.close()

    @Test
    fun uneFenetreLargePorteToutesLesPagesEtProfilSansRetour() {
        ouvrir(LARGE)
        ecran.attendreTag("barre.profil")
        ecran.attendreTag("barre.a-propos")
        ecran.attendreDisparition(hasText("Plus"))
        ecran.toucherTag("barre.profil")
        ecran.attendreTag("barre.profil")
        ecran.onNode(hasTestTag("barre.profil")).assertIsSelected()
        ecran.attendreDisparition(hasContentDescription("Retour"))
    }

    @Test
    fun repasserEnFenetreEtroiteGardeLaPageOuverte() {
        ouvrir(LARGE)
        ecran.toucherTag("barre.a-propos")
        ecran.runOnUiThread { largeur = 400.dp }
        ecran.attendreDisparition(hasTestTag("barre.a-propos"))
        // La même page, dans « Plus » : la barre du bas, et le retour vers la liste de « Plus ».
        ecran.attendreTexte("Plus")
        ecran.attendre(hasContentDescription("Retour"))
        ecran.attendreTexte("À propos")
    }

    private companion object {
        /** Juste au-dessus du seuil (600 dp) : la barre latérale et la page tiennent sur l'émulateur du téléphone. */
        val LARGE = 640.dp
    }
}
