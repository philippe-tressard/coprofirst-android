// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.entree

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.coprofirst.app.commun.attendreTexte
import fr.coprofirst.app.commun.toucherTag
import fr.coprofirst.app.session.Installation
import fr.coprofirst.app.session.ProtectionSession
import fr.coprofirst.app.session.SessionsGardeesEnMemoire
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * La session gardée à l'écran (#13), comme les tests `faceid.*` et `retour.*` de l'iOS :
 * l'écran de retour qui rouvre la dernière résidence, le mot de passe à côté, et
 * « Garder la session ? » après une première connexion. La biométrie est simulée
 * (`SessionsGardeesEnMemoire`) : l'invite du système ne se pilote pas.
 */
@RunWith(AndroidJUnit4::class)
class SessionGardeeInterfaceTest {
    @get:Rule
    val ecran = createComposeRule()
    private val serveur = ServeurDeTest()
    private val residence = Installation("https://residence.exemple.test", "Les Glycines")

    @After
    fun fermer() = serveur.close()

    private fun passagePrecedent(gardees: SessionsGardeesEnMemoire) = serveur.residencesEnMemoire().apply {
        retenir(residence)
        retenirIdentifiant("camille@exemple.test", residence)
        gardees.garder("residence.exemple.test", "r1")
    }

    @Test
    fun leRetourRouvreLaDerniereResidenceDOffice() {
        val gardees = SessionsGardeesEnMemoire()
        val connues = passagePrecedent(gardees)
        serveur.repondre(200) // POST /auth/refresh
        serveur.repondre(404) // GET /auth/me : sans profil, la barre reste celle de tous
        val parcours = serveur.parcours(gardees = gardees, connues = connues)
        ecran.setContent { NavigationEntree(serveur.apparence(), parcours) }
        ecran.attendreTexte("Rien de neuf")
    }

    @Test
    fun leMotDePasseResteACoteCourrielPreRempli() {
        val gardees = SessionsGardeesEnMemoire().apply { refuser = true }
        val connues = passagePrecedent(gardees)
        val parcours = serveur.parcours(gardees = gardees, connues = connues)
        ecran.setContent { NavigationEntree(serveur.apparence(), parcours) }
        ecran.onNodeWithTag(ReperesRetour.RESIDENCE).assertTextEquals("Les Glycines")
        ecran.toucherTag(ReperesRetour.MOT_DE_PASSE)
        // L'adresse et le courriel de la dernière connexion : il ne reste que le mot de passe.
        ecran.attendreTexte("camille@exemple.test")
        assertTrue(gardees.contient("residence.exemple.test"))
    }

    @Test
    fun gardeProposeeApresLaPremiereConnexionPuisDite() {
        val gardees = SessionsGardeesEnMemoire(ProtectionSession.CodeAppareil)
        val parcours = serveur.parcours(gardees = gardees)
        ecran.setContent { NavigationEntree(serveur.apparence(), parcours) }
        serveur.residence("Les Glycines")
        ecran.entrerDansLaResidence("residence.exemple.test", "Les Glycines") {
            serveur.repondre(200) // POST /auth/login
            serveur.repondre(404) // GET /auth/me
        }
        ecran.toucherTag(ReperesGarde.ACTIVER)
        ecran.attendreTexte("Protégée par le verrouillage de l'appareil")
        ecran.toucherTag(ReperesGarde.CONTINUER)
        ecran.attendreTexte("Rien de neuf")
    }
}
