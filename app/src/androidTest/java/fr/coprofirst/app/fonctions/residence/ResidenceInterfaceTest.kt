// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.residence

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.coprofirst.app.ThemeCoproFirst
import fr.coprofirst.app.fonctions.annuaire.AnnuaireSimule
import fr.coprofirst.app.fonctions.annuaire.EcranAnnuaire
import fr.coprofirst.app.fonctions.annuaire.ReperesAnnuaire
import fr.coprofirst.app.navigation.DroitsOnglet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Ma résidence et l'Annuaire à l'écran, comme l'iOS (`ResidenceUITests`), sur la résidence
 * simulée : les trois onglets, le syndic de la Fiche qui ouvre l'annuaire, l'avis d'une
 * question et ses fondements, une question posée. Les repères sont ceux de l'iOS.
 */
@RunWith(AndroidJUnit4::class)
class ResidenceInterfaceTest {
    @get:Rule
    val ecran = createComposeRule()
    private val residence = ResidenceSimulee()

    private fun ouvrir(droits: DroitsOnglet = DroitsOnglet(proprioOuCS = true, cs = true)) = ecran.setContent {
        ThemeCoproFirst { EcranMaResidence(residence, AnnuaireSimule(), droits, null) }
    }

    private fun attendre(texte: String) = ecran.waitUntil(DELAI_MS) {
        ecran.onAllNodes(hasText(texte, substring = true)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun attendreRepere(repere: String) =
        ecran.waitUntil(DELAI_MS) { ecran.onAllNodes(hasTestTag(repere)).fetchSemanticsNodes().isNotEmpty() }

    /**
     * Amène un repère à l'écran, puis le rend. Les pages défilent par `verticalScroll`
     * (pas une liste paresseuse) : `performScrollTo`, pas `performScrollToNode`.
     */
    private fun amener(repere: String) = run {
        attendreRepere(repere)
        ecran.onNodeWithTag(repere).performScrollTo()
    }

    @Test
    fun lesTroisOngletsDeLaResidence() {
        ouvrir()
        attendre("AA0-000-000") // la Fiche s'ouvre d'abord

        ecran.onNodeWithTag("onglet.carnet").performClick()
        attendre("Révision annuelle de la chaudière")
        attendre("1 visite attendue")

        ecran.onNodeWithTag("onglet.reglement").performClick()
        attendre("Puis-je installer une climatisation sur mon balcon ?")
        amener(ReperesResidence.POSER).assertIsNotEnabled() // question vide : rien à poser
    }

    @Test
    fun leSyndicDeLaFicheOuvreLAnnuaire() {
        ouvrir()
        amener(ReperesResidence.SYNDIC).performClick()
        attendre("Gestionnaire de copropriété")
        assertTrue(
            "le numéro du gestionnaire s'appelle d'un toucher",
            ecran.onAllNodes(hasTestTag(ReperesAnnuaire.APPELER).and(hasText("01 00 00 00 01"))).fetchSemanticsNodes().isNotEmpty(),
        )
        ecran.onNodeWithTag(ReperesResidence.FERMER).performClick()
        attendre("AA0-000-000")
    }

    @Test
    fun unFondementOuvreSaSectionEtMontreLExtrait() {
        ouvrir()
        ecran.onNodeWithTag("onglet.reglement").performClick()
        amener(ReperesResidence.question(1)).performClick()
        attendreRepere(ReperesResidence.FONDEMENTS)
        attendre("Renvoi sans extrait")
        assertTrue("sections pliées", ecran.onAllNodes(hasTestTag(ReperesResidence.extrait("AG1"))).fetchSemanticsNodes().isEmpty())
        amener(ReperesResidence.fondement("AG1")).performClick()
        attendreRepere(ReperesResidence.extrait("AG1"))
        attendre("Résolution 12")
    }

    @Test
    fun uneQuestionPoseePasseEnTete() {
        ouvrir()
        ecran.onNodeWithTag("onglet.reglement").performClick()
        amener(ReperesResidence.QUESTION).performTextInput("Puis-je fermer mon balcon ?")
        amener(ReperesResidence.POSER).assertIsEnabled().performClick()
        attendreRepere(ReperesResidence.question(99))
        assertEquals(listOf("Puis-je fermer mon balcon ?"), residence.posees)
    }

    @Test
    fun leLocataireNeVoitQueLaFiche() {
        ouvrir(DroitsOnglet(locataire = true))
        attendre("AA0-000-000")
        assertTrue("une seule page : pas de barre", ecran.onAllNodes(hasTestTag("onglet.fiche")).fetchSemanticsNodes().isEmpty())
        assertTrue("ni les comptes-rendus d'AG", ecran.onAllNodes(hasText("Comptes-rendus", substring = true)).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun lAnnuaireGroupeLeConseilParBatiment() {
        ecran.setContent { ThemeCoproFirst { EcranAnnuaire(AnnuaireSimule(), null) } }
        attendre("Voté en AG 2026 - 12 juin 2026")
        attendre("BÂTIMENT A")
        attendre("SANS BÂTIMENT")
        attendre("Président du Conseil Syndical")
    }

    private companion object {
        const val DELAI_MS = 10_000L
    }
}
