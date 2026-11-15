// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.detail.sondage

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.SemanticsNodeInteraction
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
import fr.coprofirst.app.commun.AvisDuGeste
import fr.coprofirst.app.commun.AvisEphemere
import fr.coprofirst.app.commun.CibleSignalement
import fr.coprofirst.app.commun.ReperesSignalement
import fr.coprofirst.app.commun.reponses.ReperesReponses
import fr.coprofirst.app.session.Installation
import fr.coprofirst.app.session.SessionOuverte
import fr.coprofirst.app.session.SessionsParHote
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * La fiche d'un sondage comme celle du site, contre le serveur simulé — les parcours des
 * tests d'interface de l'iOS (`SondageUITests`, mêmes repères) : la réponse à préciser, le
 * commentaire, la charte, le vote, les résultats ; puis le fil des commentaires — publier,
 * supprimer le sien (confirmé), signaler celui d'un voisin. Tout est inventé.
 */
@RunWith(AndroidJUnit4::class)
class FicheSondageInterfaceTest {
    @get:Rule
    val ecran = createComposeRule()
    private val serveur = SondageSimule()

    @Before
    fun ouvrir() {
        val session = SessionOuverte(Installation("https://residence.example.test", "Les Glycines"), SessionsParHote())
        val avis = AvisDuGeste()
        ecran.setContent {
            ThemeCoproFirst {
                AvisEphemere(avis, Modifier.fillMaxSize()) { FicheSondage(12, session, avis, service = serveur) }
            }
        }
        attendre(ReperesSondage.QUESTION)
    }

    private fun attendre(tag: String, texte: String? = null) = ecran.waitUntil(DELAI_MS) {
        val critere = if (texte == null) hasTestTag(tag) else hasTestTag(tag) and hasText(texte, substring = true)
        ecran.onAllNodes(critere).fetchSemanticsNodes().isNotEmpty()
    }

    private fun disparait(tag: String) = ecran.waitUntil(DELAI_MS) {
        ecran.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isEmpty()
    }

    private fun attendreTexte(texte: String) = ecran.waitUntil(DELAI_MS) {
        ecran.onAllNodes(hasText(texte, substring = true)).fetchSemanticsNodes().isNotEmpty()
    }

    /** L'élément ramené à l'écran (la fiche défile), puis touché. */
    private fun toucher(tag: String) = ecran.onNodeWithTag(tag).performScrollTo().performClick()

    private fun noeud(tag: String): SemanticsNodeInteraction = ecran.onNodeWithTag(tag).performScrollTo()

    @Test
    fun unVoteAPreciserCommenteSousLaCharte() {
        attendre(ReperesSondage.TOTAL, "29 votes")
        attendre(ReperesSondage.MANQUE, "Sélectionnez une option")

        toucher(ReperesSondage.option(4))
        attendre(ReperesSondage.PRECISION)
        attendre(ReperesSondage.MANQUE, "Merci de préciser votre réponse dans le champ prévu")
        noeud(ReperesSondage.VOTER).assertIsNotEnabled()

        noeud(ReperesSondage.PRECISION).performTextInput("Le 20 juin")
        noeud(ReperesSondage.COMMENTAIRE).performTextInput("Avec plaisir")
        attendre(ReperesSondage.MANQUE, "Vous devez accepter la charte de respect")
        toucher(ReperesSondage.CHARTE)
        disparait(ReperesSondage.MANQUE)
        noeud(ReperesSondage.VOTER).assertIsEnabled()

        toucher(ReperesSondage.VOTER)
        attendre(ReperesSondage.resultat(4), "Mon vote ✓")
        attendre(ReperesSondage.resultat(4), "Le 20 juin")
        attendre(ReperesSondage.TOTAL, "30 votes")
        attendre(ReperesReponses.ENTETE, "Commentaires (4)")
        attendreTexte("Vote enregistré")
    }

    @Test
    fun leFilDesCommentairesSeLitSeCompleteEtSeModere() {
        attendre(ReperesReponses.ENTETE, "Commentaires (3)")
        noeud(ReperesReponses.SAISIE).performTextInput("Je viendrai avec des chaises")
        toucher(ReperesReponses.ENVOYER)
        attendre(ReperesReponses.ENTETE, "Commentaires (4)")
        attendreTexte("Commentaire publié")

        // Mon commentaire (servi `peut_supprimer`) : supprimer, confirmer ; pas de « Signaler ».
        toucher(ReperesReponses.actions(33))
        attendre(ReperesReponses.supprimer(33))
        assertEquals(0, ecran.onAllNodes(hasTestTag(ReperesReponses.signaler(33))).fetchSemanticsNodes().size)
        ecran.onNodeWithTag(ReperesReponses.supprimer(33)).performClick()
        attendre(ReperesReponses.CONFIRMER_SUPPRESSION)
        ecran.onNodeWithTag(ReperesReponses.CONFIRMER_SUPPRESSION).performClick()
        disparait(ReperesReponses.reponse(33))

        // Le commentaire d'un voisin : le serveur ne m'ouvre pas sa suppression ; le signaler, motif obligatoire.
        toucher(ReperesReponses.actions(32))
        attendre(ReperesReponses.signaler(32))
        assertEquals(0, ecran.onAllNodes(hasTestTag(ReperesReponses.supprimer(32))).fetchSemanticsNodes().size)
        ecran.onNodeWithTag(ReperesReponses.signaler(32)).performClick()
        attendre(ReperesSignalement.MOTIF)
        ecran.onNodeWithTag(ReperesSignalement.ENVOYER).assertIsNotEnabled()
        ecran.onNodeWithTag(ReperesSignalement.MOTIF).performTextInput("Hors sujet")
        ecran.onNodeWithTag(ReperesSignalement.ENVOYER).performClick()
        disparait(ReperesSignalement.MOTIF)
        attendreTexte("Signalement transmis au conseil syndical")
        assertEquals(listOf(CibleSignalement.commentaire(32) to "Hors sujet"), serveur.signalements)
    }

    private companion object {
        const val DELAI_MS = 10_000L
    }
}
