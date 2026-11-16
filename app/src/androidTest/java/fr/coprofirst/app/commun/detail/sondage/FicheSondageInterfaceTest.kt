// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.detail.sondage

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.coprofirst.app.ThemeCoproFirst
import fr.coprofirst.app.commun.AvisDuGeste
import fr.coprofirst.app.commun.AvisEphemere
import fr.coprofirst.app.commun.CibleSignalement
import fr.coprofirst.app.commun.ReperesSignalement
import fr.coprofirst.app.commun.amenerTag
import fr.coprofirst.app.commun.attendreDisparitionTag
import fr.coprofirst.app.commun.attendreTag
import fr.coprofirst.app.commun.attendreTexte
import fr.coprofirst.app.commun.reponses.ReperesReponses
import fr.coprofirst.app.commun.toucherTag
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
        ecran.attendreTag(ReperesSondage.QUESTION)
    }

    @Test
    fun unVoteAPreciserCommenteSousLaCharte() {
        ecran.attendreTag(ReperesSondage.TOTAL, "29 votes")
        ecran.attendreTag(ReperesSondage.MANQUE, "Sélectionnez une option")

        ecran.toucherTag(ReperesSondage.option(4))
        ecran.attendreTag(ReperesSondage.PRECISION)
        ecran.attendreTag(ReperesSondage.MANQUE, "Merci de préciser votre réponse dans le champ prévu")
        ecran.amenerTag(ReperesSondage.VOTER).assertIsNotEnabled()

        ecran.amenerTag(ReperesSondage.PRECISION).performTextInput("Le 20 juin")
        ecran.amenerTag(ReperesSondage.COMMENTAIRE).performTextInput("Avec plaisir")
        ecran.attendreTag(ReperesSondage.MANQUE, "Vous devez accepter la charte de respect")
        ecran.toucherTag(ReperesSondage.CHARTE)
        ecran.attendreDisparitionTag(ReperesSondage.MANQUE)
        ecran.amenerTag(ReperesSondage.VOTER).assertIsEnabled()

        ecran.toucherTag(ReperesSondage.VOTER)
        ecran.attendreTag(ReperesSondage.resultat(4), "Mon vote ✓")
        ecran.attendreTag(ReperesSondage.resultat(4), "Le 20 juin")
        ecran.attendreTag(ReperesSondage.TOTAL, "30 votes")
        ecran.attendreTag(ReperesReponses.ENTETE, "COMMENTAIRES (4)")
        ecran.attendreTexte("Vote enregistré")
    }

    @Test
    fun leFilDesCommentairesSeLitSeCompleteEtSeModere() {
        ecran.attendreTag(ReperesReponses.ENTETE, "COMMENTAIRES (3)")
        ecran.amenerTag(ReperesReponses.SAISIE).performTextInput("Je viendrai avec des chaises")
        ecran.toucherTag(ReperesReponses.ENVOYER)
        ecran.attendreTag(ReperesReponses.ENTETE, "COMMENTAIRES (4)")
        ecran.attendreTexte("Commentaire publié")

        // Mon commentaire (servi `peut_supprimer`) : supprimer, confirmer ; pas de « Signaler ».
        ecran.toucherTag(ReperesReponses.actions(33))
        ecran.attendreTag(ReperesReponses.supprimer(33))
        assertEquals(0, ecran.onAllNodes(hasTestTag(ReperesReponses.signaler(33))).fetchSemanticsNodes().size)
        ecran.toucherTag(ReperesReponses.supprimer(33))
        ecran.attendreTag(ReperesReponses.CONFIRMER_SUPPRESSION)
        ecran.toucherTag(ReperesReponses.CONFIRMER_SUPPRESSION)
        ecran.attendreDisparitionTag(ReperesReponses.reponse(33))

        // Le commentaire d'un voisin : le serveur ne m'ouvre pas sa suppression ; le signaler, motif obligatoire.
        ecran.toucherTag(ReperesReponses.actions(32))
        ecran.attendreTag(ReperesReponses.signaler(32))
        assertEquals(0, ecran.onAllNodes(hasTestTag(ReperesReponses.supprimer(32))).fetchSemanticsNodes().size)
        ecran.toucherTag(ReperesReponses.signaler(32))
        ecran.attendreTag(ReperesSignalement.MOTIF)
        ecran.onNodeWithTag(ReperesSignalement.ENVOYER).assertIsNotEnabled()
        ecran.onNodeWithTag(ReperesSignalement.MOTIF).performTextInput("Hors sujet")
        ecran.toucherTag(ReperesSignalement.ENVOYER)
        ecran.attendreDisparitionTag(ReperesSignalement.MOTIF)
        ecran.attendreTexte("Signalement transmis au conseil syndical")
        assertEquals(listOf(CibleSignalement.commentaire(32) to "Hors sujet"), serveur.signalements)
    }

    private companion object {
        const val DELAI_MS = 10_000L
    }
}
