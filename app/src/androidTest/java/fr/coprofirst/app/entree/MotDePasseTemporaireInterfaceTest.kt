// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.entree

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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * #371, comme `ConnexionUITests.testUnMotDePasseTemporaireSeChoisitAvantTout` de l'iOS : un
 * compte au mot de passe temporaire (`mot_de_passe_a_changer` de GET /auth/me) n'a que
 * « Choisir votre mot de passe » et la déconnexion ; le mot de passe choisi, le compte relu,
 * la résidence s'ouvre.
 */
@RunWith(AndroidJUnit4::class)
class MotDePasseTemporaireInterfaceTest {
    @get:Rule
    val ecran = createComposeRule()
    private val serveur = ServeurDeTest()

    @Before
    fun ouvrir() {
        val parcours = serveur.parcours()
        ecran.setContent { NavigationEntree(serveur.apparence(), parcours) }
        ecran.toucherTexte("Entrer dans ma résidence")
    }

    @After
    fun fermer() = serveur.close()

    private fun moi(temporaire: Boolean) = FixturesProfil.MOI.replaceFirst("{", """{"mot_de_passe_a_changer": $temporaire, """)

    @Test
    fun unMotDePasseTemporaireSeChoisitAvantTout() {
        serveur.residence("Les Glycines")
        ecran.onNodeWithTag(Reperes.RESIDENCE).performTextInput("initiale.exemple.test")
        ecran.attendreTexte("Les Glycines")
        ecran.onNodeWithTag(Reperes.EMAIL).performTextInput("admin@exemple.test")
        ecran.onNodeWithTag(Reperes.MOT_DE_PASSE).performTextInput("temporaire-1")
        ecran.toucherTag(Reperes.VALIDER)
        serveur.repondre(200) // POST /auth/login
        serveur.repondre(200, moi(temporaire = true)) // GET /auth/me : le drapeau
        ecran.toucherTexte("Me connecter à cette adresse")

        // La porte : le formulaire, à ses libellés, la déconnexion — ni la barre, ni « Plus ».
        ecran.attendreTag("profil.motDePasse.actuel")
        ecran.attendreTexte("Choisir votre mot de passe")
        ecran.attendreTexte("Mot de passe temporaire")
        ecran.onNodeWithTag(ReperesPorteMotDePasse.DECONNECTER).assertExists()
        assertEquals("pas de « Plus » derrière la porte", 0, ecran.onAllNodes(hasText("Plus")).fetchSemanticsNodes().size)

        ecran.onNodeWithTag("profil.motDePasse.actuel").performTextInput("temporaire-1")
        ecran.onNodeWithTag("profil.motDePasse.nouveau").performTextInput("un-vrai-secret")
        ecran.onNodeWithTag("profil.motDePasse.confirmation").performTextInput("un-vrai-secret")
        serveur.repondre(200) // POST /auth/change-password
        serveur.repondre(200, moi(temporaire = false)) // GET /auth/me relu : le drapeau tombé
        ecran.toucherTag("profil.motDePasse.modifier")

        // La résidence ouverte : le fil, et « Plus » dans la barre.
        ecran.attendreTexte("Rien de neuf")
        ecran.waitUntil(DELAI_MS) { ecran.onAllNodes(hasText("Plus")).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(0, ecran.onAllNodes(hasTestTag("profil.motDePasse.actuel")).fetchSemanticsNodes().size)
        val changement = generateSequence { serveur.serveur.takeRequest(0, TimeUnit.SECONDS) }
            .firstOrNull { it.url.encodedPath == "/api/auth/change-password" }?.body?.utf8().orEmpty()
        assertTrue("le temporaire part comme l'actuel : $changement", "\"mot_de_passe_actuel\":\"temporaire-1\"" in changement)
        assertTrue("le nouveau part : $changement", "\"nouveau_mot_de_passe\":\"un-vrai-secret\"" in changement)
    }

    private companion object {
        const val DELAI_MS = 10_000L
    }
}
