// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.residence

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.coprofirst.app.ThemeCoproFirst
import fr.coprofirst.app.commun.amenerTag
import fr.coprofirst.app.commun.annuaire.ReperesContact
import fr.coprofirst.app.commun.attendre
import fr.coprofirst.app.commun.attendreTag
import fr.coprofirst.app.commun.attendreTexte
import fr.coprofirst.app.commun.toucher
import fr.coprofirst.app.commun.toucherTag
import fr.coprofirst.app.fonctions.annuaire.AnnuaireSimule
import fr.coprofirst.app.fonctions.annuaire.EcranAnnuaire
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

    @Test
    fun lesTroisOngletsDeLaResidence() {
        ouvrir()
        ecran.attendreTexte("AA0-000-000") // la Fiche s'ouvre d'abord

        ecran.toucherTag("onglet.carnet")
        ecran.attendreTexte("Révision annuelle de la chaudière")
        ecran.attendreTexte("1 visite attendue")
        // Un contrat qui n'a pas commencé : « À venir » sous « Contrat », lu avec sa date (#220).
        ecran.attendre(hasContentDescription("À venir", substring = true))

        ecran.toucherTag("onglet.reglement")
        ecran.attendreTexte("Puis-je installer une climatisation sur mon balcon ?")
        ecran.amenerTag(ReperesResidence.POSER).assertIsNotEnabled() // question vide : rien à poser
    }

    @Test
    fun leSyndicDeLaFicheOuvreLAnnuaire() {
        ouvrir()
        ecran.toucherTag(ReperesResidence.SYNDIC)
        ecran.attendreTexte("Gestionnaire de copropriété")
        assertTrue(
            "le numéro du gestionnaire s'appelle d'un toucher",
            ecran.onAllNodes(hasTestTag(ReperesContact.APPELER).and(hasText("01 00 00 00 01"))).fetchSemanticsNodes().isNotEmpty(),
        )
        ecran.toucherTag(ReperesResidence.FERMER)
        ecran.attendreTexte("AA0-000-000")
    }

    @Test
    fun unFondementOuvreSaSectionEtMontreLExtrait() {
        ouvrir()
        ecran.toucherTag("onglet.reglement")
        ecran.toucherTag(ReperesResidence.question(1))
        ecran.attendreTag(ReperesResidence.FONDEMENTS)
        ecran.attendreTexte("Renvoi sans extrait")
        assertTrue("sections pliées", ecran.onAllNodes(hasTestTag(ReperesResidence.extrait("AG1"))).fetchSemanticsNodes().isEmpty())
        ecran.toucherTag(ReperesResidence.fondement("AG1"))
        ecran.attendreTag(ReperesResidence.extrait("AG1"))
        ecran.attendreTexte("Résolution 12")
    }

    @Test
    fun uneQuestionPoseePasseEnTete() {
        ouvrir()
        ecran.toucherTag("onglet.reglement")
        ecran.amenerTag(ReperesResidence.QUESTION).performTextInput("Puis-je fermer mon balcon ?")
        ecran.amenerTag(ReperesResidence.POSER).assertIsEnabled().toucher()
        ecran.attendreTag(ReperesResidence.question(99))
        assertEquals(listOf("Puis-je fermer mon balcon ?"), residence.posees)
    }

    @Test
    fun leLocataireNeVoitQueLaFiche() {
        ouvrir(DroitsOnglet(locataire = true))
        ecran.attendreTexte("AA0-000-000")
        assertTrue("une seule page : pas de barre", ecran.onAllNodes(hasTestTag("onglet.fiche")).fetchSemanticsNodes().isEmpty())
        assertTrue("ni les comptes-rendus d'AG", ecran.onAllNodes(hasText("Comptes-rendus", substring = true)).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun lAnnuaireGroupeLeConseilParBatiment() {
        ecran.setContent { ThemeCoproFirst { EcranAnnuaire(AnnuaireSimule(), null) } }
        ecran.attendreTexte("Voté en AG 2026 - 12 juin 2026")
        ecran.attendreTexte("BÂTIMENT A")
        ecran.attendreTexte("SANS BÂTIMENT")
        ecran.attendreTexte("Président du Conseil Syndical")
    }

    private companion object {
        const val DELAI_MS = 10_000L
    }
}
