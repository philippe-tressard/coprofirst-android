// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.affaires

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.coprofirst.app.ThemeCoproFirst
import fr.coprofirst.app.commun.AvisDuGeste
import fr.coprofirst.app.commun.AvisEphemere
import fr.coprofirst.app.commun.attendreTag
import fr.coprofirst.app.commun.attendreTexte
import fr.coprofirst.app.commun.residenceSimulee
import fr.coprofirst.app.commun.toucher
import fr.coprofirst.app.session.SessionOuverte
import fr.coprofirst.app.session.SessionsParHote
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** #281 : SON message du fil se modifie puis se supprime, comme l'iOS (`MessagesAffaireUITests`). */
@RunWith(AndroidJUnit4::class)
class MessagesAffaireInterfaceTest {
    @get:Rule
    val ecran = createComposeRule()

    private val session = SessionOuverte(residenceSimulee("https://residence.exemple.test", "Les Glycines"), SessionsParHote())

    private fun ouvrirFiche(id: Int): AffairesSimulees {
        val service = AffairesSimulees()
        val avis = AvisDuGeste()
        val fiche = ContexteFiche(service, session, false, avis, null) { "https://residence.exemple.test/tickets/$it" }
        ecran.setContent { ThemeCoproFirst { AvisEphemere(avis) { FicheAffaire(id, fiche) } } }
        return service
    }

    private fun el(tag: String): SemanticsNodeInteraction = ecran.onNodeWithTag(tag, useUnmergedTree = true)

    @Test
    fun sonMessageSeModifiePuisSeSupprime() {
        val service = ouvrirFiche(1)
        ecran.attendreTag(ReperesAffaires.gestesMessage(41), useUnmergedTree = true)
        el(ReperesAffaires.gestesMessage(41)).toucher()
        ecran.attendreTag(ReperesAffaires.gesteMessage(GesteSuite.Modifier), useUnmergedTree = true)
        el(ReperesAffaires.gesteMessage(GesteSuite.Modifier)).toucher()
        ecran.attendreTag(ReperesFeuilles.REPONDRE_TEXTE, useUnmergedTree = true)
        // La feuille part du texte servi : on le remplace.
        el(ReperesFeuilles.REPONDRE_TEXTE).performTextReplacement("Merci pour le suivi, à jeudi.")
        el(ReperesFeuilles.REPONDRE_ENVOYER).toucher()
        ecran.attendreTexte("Message modifié", useUnmergedTree = true)
        assertEquals(listOf(41 to "Merci pour le suivi, à jeudi."), service.messagesCorriges)

        el(ReperesAffaires.gestesMessage(41)).toucher()
        ecran.attendreTag(ReperesAffaires.gesteMessage(GesteSuite.Supprimer), useUnmergedTree = true)
        el(ReperesAffaires.gesteMessage(GesteSuite.Supprimer)).toucher()
        ecran.attendreTexte("Supprimer ce message ? Il disparaît aussi de l’Historique.", useUnmergedTree = true)
        el(ReperesAffaires.CONFIRMER_SUPPRESSION_MESSAGE).toucher()
        ecran.attendreTexte("Message supprimé", useUnmergedTree = true)
        assertEquals(listOf(41), service.messagesSupprimes)
    }
}
