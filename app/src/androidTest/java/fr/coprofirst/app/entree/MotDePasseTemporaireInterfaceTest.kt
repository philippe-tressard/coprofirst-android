// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.entree

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.coprofirst.app.commun.attendreTag
import fr.coprofirst.app.commun.attendreTexte
import fr.coprofirst.app.commun.toucherTag
import fr.coprofirst.app.commun.toucherTexte
import fr.coprofirst.app.fonctions.profil.FixturesProfil
import fr.coprofirst.app.session.ProtectionSession
import fr.coprofirst.app.session.SessionsGardeesEnMemoire
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * #371, comme `ConnexionUITests.testUnMotDePasseTemporaireSeChoisitAvantTout` de l'iOS : un
 * compte au mot de passe temporaire (`mot_de_passe_a_changer` de GET /auth/me) n'a que
 * « Choisir votre mot de passe » et la déconnexion ; le mot de passe choisi, le compte relu,
 * la résidence s'ouvre. #391, comme `testUneConnexionFraicheMeneDroitALaPorteDuMotDePasseTemporaire`
 * (coprofirst-ios#405) : la connexion lit le compte AVANT d'ouvrir — la porte vient seule, jamais
 * le fil refusé avant elle, ni « Garder la session ? » par-dessus ; le succès se sent.
 */
@RunWith(AndroidJUnit4::class)
class MotDePasseTemporaireInterfaceTest {
    @get:Rule
    val ecran = createComposeRule()
    private val serveur = ServeurDeTest()

    /** Les retours haptiques joués, dans l'ordre. */
    private val retours = mutableListOf<HapticFeedbackType>()

    /** L'entrée, sur un appareil qui garde les sessions s'il le faut (« Garder la session ? »). */
    private fun ouvrir(parcours: ParcoursEntree = serveur.parcours()) {
        val haptique = object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                retours += hapticFeedbackType
            }
        }
        ecran.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides haptique) { NavigationEntree(serveur.apparence(), parcours) }
        }
        ecran.toucherTexte("Entrer dans ma résidence")
    }

    /** La résidence reconnue, les identifiants saisis, « Se connecter » touché : la confirmation de l'hôte. */
    private fun jusquALaConfirmation() {
        serveur.residence("Les Glycines")
        ecran.onNodeWithTag(Reperes.RESIDENCE).performTextInput("initiale.exemple.test")
        ecran.attendreTexte("Les Glycines")
        ecran.onNodeWithTag(Reperes.EMAIL).performTextInput("admin@exemple.test")
        ecran.onNodeWithTag(Reperes.MOT_DE_PASSE).performTextInput("temporaire-1")
        ecran.toucherTag(Reperes.VALIDER)
    }

    /** Les champs de la porte remplis, « Choisir mon mot de passe » touché ; le serveur répond `reponses`. */
    private fun choisir(reponses: () -> Unit) {
        ecran.onNodeWithTag("profil.motDePasse.actuel").performTextInput("temporaire-1")
        ecran.onNodeWithTag("profil.motDePasse.nouveau").performTextInput("un-vrai-secret")
        ecran.onNodeWithTag("profil.motDePasse.confirmation").performTextInput("un-vrai-secret")
        reponses()
        ecran.toucherTag("profil.motDePasse.modifier")
    }

    /** Les chemins demandés au serveur jusqu'ici (la file des requêtes se vide). */
    private fun chemins() = generateSequence { serveur.serveur.takeRequest(0, TimeUnit.SECONDS) }.map { it.url.encodedPath }.toList()

    @After
    fun fermer() = serveur.close()

    private fun moi(temporaire: Boolean) = FixturesProfil.MOI.replaceFirst("{", """{"mot_de_passe_a_changer": $temporaire, """)

    @Test
    fun unMotDePasseTemporaireSeChoisitAvantTout() {
        ouvrir()
        jusquALaConfirmation()
        serveur.repondre(200) // POST /auth/login
        serveur.repondre(200, moi(temporaire = true)) // GET /auth/me : le drapeau
        ecran.toucherTexte("Me connecter à cette adresse")

        // La porte : le formulaire, à ses libellés, la déconnexion — ni la barre, ni « Plus ».
        ecran.attendreTag("profil.motDePasse.actuel")
        ecran.attendreTexte("Choisir votre mot de passe")
        ecran.attendreTexte("Mot de passe temporaire")
        ecran.onNodeWithTag(ReperesPorteMotDePasse.DECONNECTER).assertExists()
        assertEquals("pas de « Plus » derrière la porte", 0, ecran.onAllNodes(hasText("Plus")).fetchSemanticsNodes().size)

        choisir {
            serveur.repondre(200) // POST /auth/change-password
            serveur.repondre(200, moi(temporaire = false)) // GET /auth/me relu : le drapeau tombé
        }

        // La résidence ouverte : le fil, et « Plus » dans la barre.
        ecran.attendreTexte("Rien de neuf")
        ecran.waitUntil(DELAI_MS) { ecran.onAllNodes(hasText("Plus")).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(0, ecran.onAllNodes(hasTestTag("profil.motDePasse.actuel")).fetchSemanticsNodes().size)
        val changement = generateSequence { serveur.serveur.takeRequest(0, TimeUnit.SECONDS) }
            .firstOrNull { it.url.encodedPath == "/api/auth/change-password" }?.body?.utf8().orEmpty()
        assertTrue("le temporaire part comme l'actuel : $changement", "\"mot_de_passe_actuel\":\"temporaire-1\"" in changement)
        assertTrue("le nouveau part : $changement", "\"nouveau_mot_de_passe\":\"un-vrai-secret\"" in changement)
    }

    @Test
    fun uneConnexionFraicheMeneDroitALaPorte() {
        ouvrir(serveur.parcours(gardees = SessionsGardeesEnMemoire(ProtectionSession.Biometrie)))
        jusquALaConfirmation()
        serveur.repondre(200) // POST /auth/login ; GET /auth/me attend sa réponse
        ecran.toucherTexte("Me connecter à cette adresse")

        // Le compte se lit : la connexion reste, son bouton en attente — rien de la résidence.
        val avant = mutableListOf<String>()
        ecran.waitUntil(DELAI_MS) { avant += chemins(); "/api/auth/me" in avant }
        ecran.waitForIdle()
        ecran.onNodeWithTag(Reperes.VALIDER).assertExists()
        assertEquals("ni la barre ni « Plus » avant le compte", 0, ecran.onAllNodes(hasText("Plus")).fetchSemanticsNodes().size)

        serveur.repondre(200, moi(temporaire = true))
        ecran.attendreTag("profil.motDePasse.actuel")
        ecran.attendreTexte("Choisir votre mot de passe")
        assertEquals("« Garder la session ? » attend la porte levée", 0, ecran.onAllNodes(hasTestTag(ReperesGarde.PLUS_TARD)).fetchSemanticsNodes().size)

        choisir {
            serveur.repondre(200) // POST /auth/change-password
            serveur.repondre(200, moi(temporaire = false)) // GET /auth/me relu
        }
        // La porte levée : la résidence, puis « Garder la session ? » ; le succès s'est senti.
        ecran.attendreTag(ReperesGarde.PLUS_TARD)
        assertTrue("le retour du succès : $retours", HapticFeedbackType.Confirm in retours)
        val apres = chemins()
        assertFalse("le fil ne s'est pas lu avant la porte : $avant", "/api/flux" in avant)
        val changement = apres.indexOf("/api/auth/change-password")
        assertTrue("le mot de passe choisi : $apres", changement >= 0)
        assertFalse("le fil ne s'est pas lu avant la porte : $apres", "/api/flux" in apres.take(changement))
    }

    private companion object {
        const val DELAI_MS = 10_000L
    }
}
