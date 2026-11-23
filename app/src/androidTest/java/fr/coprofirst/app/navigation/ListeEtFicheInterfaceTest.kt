// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.coprofirst.app.ThemeCoproFirst
import fr.coprofirst.app.charte.Palettes
import fr.coprofirst.app.commun.GestesDeSortie
import fr.coprofirst.app.commun.attendre
import fr.coprofirst.app.commun.attendreDisparition
import fr.coprofirst.app.commun.attendreDisparitionTag
import fr.coprofirst.app.commun.attendreTag
import fr.coprofirst.app.commun.attendreTexte
import fr.coprofirst.app.commun.miseenpage.REPERE_COLONNE
import fr.coprofirst.app.commun.residenceSimulee
import fr.coprofirst.app.commun.toucherDescription
import fr.coprofirst.app.commun.toucherTag
import fr.coprofirst.app.entree.ServeurDeTest
import fr.coprofirst.app.fonctions.fil.FilInterfaceTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * #69, comme `ListeEtFicheUITests` de l'iOS (coprofirst-ios#261) : en fenêtre large, une liste
 * et la fiche qu'elle ouvre se tiennent côte à côte — la page garde son titre, la carte se
 * souligne, la colonne dit « Aucune sélection » tant que rien n'est choisi. En fenêtre étroite,
 * la fiche se pousse, comme avant. Largeur IMPOSÉE au cadre : joué sur l'émulateur du téléphone.
 */
@RunWith(AndroidJUnit4::class)
class ListeEtFicheInterfaceTest {
    @get:Rule
    val ecran = createComposeRule()
    private val serveur = ServeurDeTest()

    private val sortie = object : GestesDeSortie {
        override fun deconnecter() = Unit
        override fun oublierTout() = Unit
    }

    /** L'écran affiché ; `false` le temps d'une reconstruction. */
    private var affiche by mutableStateOf(true)

    /** Ce que l'écran a sauvé (`rememberSaveable`) avant sa reconstruction. */
    private var sauve: Map<String, List<Any?>>? = null
    private var registre: SaveableStateRegistry? = null

    /**
     * La reconstruction de l'écran (rotation, pli, multi-fenêtre), comme Android la fait : l'état
     * `rememberSaveable` est SAUVÉ, la composition détruite puis recréée depuis lui ; le ViewModel
     * survit (même `ViewModelStore`). Ce que fait `StateRestorationTester`, expérimental et lié à
     * l'ancienne règle de test.
     */
    private fun reconstruire() {
        ecran.runOnUiThread {
            sauve = registre?.performSave()
            affiche = false
        }
        ecran.waitForIdle()
        ecran.runOnUiThread { affiche = true }
    }

    /** La taille du cadre : la tablette se tourne sans reconstruire l'écran (multi-fenêtre). */
    private var taille by mutableStateOf(DpSize(LARGE, PAYSAGE))

    private fun ouvrir(largeur: Dp, hauteur: Dp = PAYSAGE) {
        taille = DpSize(largeur, hauteur)
        serveur.flux = FilInterfaceTest.fil(Instant.now())
        val session = serveur.session(residenceSimulee("https://residence.exemple.test", "Les Glycines"))
        ecran.setContent {
            if (affiche) {
                val actuel = remember { SaveableStateRegistry(sauve) { true }.also { registre = it } }
                CompositionLocalProvider(LocalSaveableStateRegistry provides actuel) {
                    ThemeCoproFirst(Palettes.pour(null), sombre = false) {
                        // Calé en haut à gauche : plus large que l'écran du téléphone, il ne déborde qu'à droite.
                        Box(Modifier.wrapContentSize(Alignment.TopStart, unbounded = true).requiredSize(taille)) {
                            EcranResidence(session, sortie, serveur.apparence())
                        }
                    }
                }
            }
        }
        ecran.attendreTag(CARTE)
    }

    @After
    fun fermer() = serveur.close()

    @Test
    fun enFenetreLargeLaFicheSOuvreACoteDeLaListe() {
        ouvrir(LARGE)
        ecran.attendreTag(REPERE_COLONNE)
        ecran.attendreTexte("Aucune sélection")
        ecran.toucherTag(CARTE)
        ecran.attendreDisparition(hasText("Aucune sélection"))
        // La page et sa liste restent, la carte est soulignée ; la fiche est dans la colonne, sans retour.
        ecran.onNodeWithTag(CARTE).assertIsSelected()
        ecran.attendreTexte(PagesParDefaut.TITRE_DU_FIL)
        ecran.attendreTag(REPERE_COLONNE)
        ecran.attendreDisparition(hasContentDescription("Retour"))
        // Sans flèche dans la colonne, le geste « retour » du système referme la fiche.
        ecran.fermerLaFiche()
        ecran.attendreTexte("Aucune sélection")
    }

    /**
     * La règle de la mémoire des sélections (`MemoireDesSelections` de l'iOS) : l'élément ouvert
     * survit à la reconstruction de l'écran — rotation, pli, multi-fenêtre. Sauvé puis restauré,
     * l'écran rouvre la même fiche, à côté de la même carte.
     */
    @Test
    fun laFicheOuverteSurvitALaReconstructionDeLEcran() {
        ouvrir(LARGE)
        ecran.toucherTag(CARTE)
        ecran.attendreDisparition(hasText("Aucune sélection"))
        reconstruire()
        ecran.attendreTag(CARTE)
        ecran.onNodeWithTag(CARTE).assertIsSelected()
        ecran.attendreDisparition(hasText("Aucune sélection"))
    }

    @Test
    fun enFenetreEtroiteLaFichePousseeSurvitALaReconstruction() {
        ouvrir(ETROITE)
        ecran.toucherTag(CARTE)
        ecran.attendre(hasContentDescription("Retour"))
        reconstruire()
        ecran.attendre(hasContentDescription("Retour"))
        ecran.attendreDisparitionTag(CARTE)
    }

    @Test
    fun enFenetreEtroiteLaFicheSePousse() {
        ouvrir(ETROITE)
        ecran.attendreDisparitionTag(REPERE_COLONNE)
        ecran.toucherTag(CARTE)
        ecran.attendre(hasContentDescription("Retour"))
        ecran.attendreDisparitionTag(CARTE)
    }

    /**
     * coprofirst-ios#308 : une fenêtre large EN PORTRAIT garde la barre latérale, mais la liste
     * prend toute la page et pousse sa fiche par-dessus elle, avec un retour vers la liste.
     */
    @Test
    fun enPortraitLaFicheSePousseParDessusLaListe() {
        ouvrir(LARGE, PORTRAIT)
        ecran.attendre(hasTestTag(BARRE_LATERALE))
        ecran.attendreDisparitionTag(REPERE_COLONNE)
        ecran.toucherTag(CARTE)
        ecran.attendre(hasContentDescription("Retour"))
        ecran.attendreDisparitionTag(CARTE)
        ecran.attendreDisparitionTag(REPERE_COLONNE)
        ecran.attendre(hasTestTag(BARRE_LATERALE))
        ecran.toucherDescription("Retour")
        ecran.attendreTag(CARTE)
    }

    /** La tablette tournée : la fiche poussée en portrait se tient à côté de sa liste en paysage, et inversement. */
    @Test
    fun laFicheSuitLaTabletteQuOnTourne() {
        ouvrir(LARGE, PORTRAIT)
        ecran.toucherTag(CARTE)
        ecran.attendreDisparitionTag(CARTE)
        taille = DpSize(LARGE, PAYSAGE)
        ecran.attendreTag(CARTE)
        ecran.onNodeWithTag(CARTE).assertIsSelected()
        ecran.attendreTag(REPERE_COLONNE)
        ecran.attendreDisparition(hasText("Aucune sélection"))
        taille = DpSize(LARGE, PORTRAIT)
        ecran.attendre(hasContentDescription("Retour"))
        ecran.attendreDisparitionTag(CARTE)
    }

    private companion object {
        /** La première carte du fil (l'affaire 41) : à l'écran même quand la liste n'a que 190 dp. */
        const val CARTE = "fil.ticket_ouvert:41"

        /** Juste au-dessus du seuil : la barre latérale et la liste tiennent sur l'émulateur du téléphone. */
        val LARGE = 640.dp
        val ETROITE = 400.dp

        /** Les hauteurs du cadre : plus basse que la largeur (paysage), plus haute (portrait). */
        val PAYSAGE = 600.dp
        val PORTRAIT = 1000.dp

        /** Profil, toujours dans la barre latérale (`barre.<id>`, le repère de l'iOS). */
        const val BARRE_LATERALE = "barre.profil"
    }
}
