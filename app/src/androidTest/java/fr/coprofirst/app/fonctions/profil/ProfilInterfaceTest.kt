// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.profil

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.coprofirst.app.commun.amenerTag
import fr.coprofirst.app.commun.toucherTag
import fr.coprofirst.app.commun.toucherTexte
import fr.coprofirst.app.entree.NavigationEntree
import fr.coprofirst.app.entree.entrerDansLaResidence
import fr.coprofirst.app.entree.ServeurDeTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Plus › Profil, comme l'iOS (`SuppressionCompteUITests`) : la demande de suppression du
 * compte depuis l'app — ce qui sera effacé dit d'abord, le mot de passe exigé, la phrase
 * du serveur sur un refus, la demande retirable. Contre un serveur local en HTTPS, qui
 * refuse le mot de passe « refuse » comme le ferait le vrai.
 */
@RunWith(AndroidJUnit4::class)
class ProfilInterfaceTest {
    @get:Rule
    val ecran = createComposeRule()
    private val serveur = ServeurDeTest()

    /** La demande en cours, côté serveur. */
    @Volatile
    private var demandeOuverte = false
    private val motsDePasseRecus = mutableListOf<String>()
    private val saisies = mutableListOf<String>()

    private val residence = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val chemin = request.url.encodedPath
            return when {
                chemin.endsWith("/config") -> json(200, """{"site_nom": "Les Glycines"}""")
                chemin.endsWith("/auth/login") || chemin.endsWith("/auth/logout") -> json(200, "{}")
                chemin.endsWith("/auth/me") -> {
                    if (request.method == "PATCH") synchronized(saisies) { saisies += request.body?.utf8().orEmpty() }
                    json(200, FixturesProfil.MOI)
                }
                chemin.endsWith("/auth/demande-suppression") -> suppression(request)
                else -> json(404, """{"detail": "Introuvable."}""")
            }
        }
    }

    private fun suppression(request: RecordedRequest): MockResponse = when (request.method) {
        "POST" -> {
            val corps = request.body?.utf8().orEmpty()
            synchronized(motsDePasseRecus) { motsDePasseRecus += corps }
            if ("\"refuse\"" in corps) {
                json(400, FixturesProfil.MOT_DE_PASSE_INCORRECT)
            } else {
                demandeOuverte = true
                json(201, FixturesProfil.DEMANDE)
            }
        }
        "DELETE" -> {
            demandeOuverte = false
            MockResponse.Builder().code(204).build()
        }
        else -> json(200, if (demandeOuverte) FixturesProfil.ETAT_AVEC_DEMANDE else FixturesProfil.ETAT_SANS_DEMANDE)
    }

    private fun json(code: Int, corps: String) =
        MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(corps).build()

    @Before
    fun ouvrirLeProfil() {
        serveur.serveur.dispatcher = residence
        val parcours = serveur.parcours()
        ecran.setContent { NavigationEntree(serveur.apparence(), parcours) }
        ecran.entrerDansLaResidence("residence.exemple.test", "Les Glycines")
        attendre("Plus")
        ecran.toucherTexte("Plus")
        ecran.toucherTag("plus.profil")
        attendre("Camille EXEMPLE")
    }

    @After
    fun fermer() = serveur.close()

    private fun attendre(texte: String) = ecran.waitUntil(DELAI_MS) {
        ecran.onAllNodes(hasText(texte, substring = true)).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun leProfilDitLaResidenceEtLeStatutServis() {
        attendre("Bât. A, T3, 2ème")
        attendre("Copropriétaire résident")
        attendre("Conseil syndical")
    }

    /** Le thème de l'appareil se choisit au Profil ; la sortie y est, et se confirme. */
    @Test
    fun leThemeSeChoisitEtLaSortieSeConfirme() {
        ecran.toucherTag("profil.theme.sombre")
        ecran.toucherTag("profil.deconnecter")
        ecran.toucherTag("sortie.confirmer")
        attendre("Connectez-vous à votre espace")
    }

    @Test
    fun laDemandeSeFaitAvecLeMotDePasseEtSeRetire() {
        ecran.toucherTag("profil.suppression.demander")

        // La feuille dit ce qui sera effacé (phrases servies) avant de demander quoi que ce soit.
        attendre("Ancien résident")
        ecran.onNodeWithTag("suppression.confirmer").assertIsNotEnabled()

        // Un mot de passe refusé par le serveur : sa phrase, la feuille reste.
        val motDePasse = ecran.onNodeWithTag("suppression.motDePasse")
        motDePasse.performTextInput("refuse")
        ecran.toucherTag("suppression.confirmer")
        attendre("Mot de passe incorrect.")

        // Le bon mot de passe : la demande part, la feuille se ferme, on peut la retirer.
        motDePasse.performTextClearance()
        motDePasse.performTextInput("secret")
        ecran.toucherTag("suppression.confirmer")
        attendre("Un accusé de réception vous est adressé.")
        ecran.amenerTag("profil.suppression.retirer")
        assertTrue(ecran.onAllNodes(hasTestTag("profil.suppression.demander")).fetchSemanticsNodes().isEmpty())
        assertTrue("le mot de passe est parti, au serveur local seul", motsDePasseRecus.size == 2)

        // Retirée : le compte est conservé, le geste revient.
        ecran.toucherTag("profil.suppression.retirer")
        attendre("votre compte est conservé")
        ecran.amenerTag("profil.suppression.demander")
    }

    /** #47 : la saisie d'une affaire se choisit au Profil, part sur le compte, et la pastille suit. */
    @Test
    fun laSaisieDUneAffaireSeChoisitSurLeCompte() {
        ecran.toucherTag("profil.saisie.complete")
        ecran.waitUntil(DELAI_MS) { synchronized(saisies) { saisies.any { "\"saisie_affaire\":\"complete\"" in it.replace(" ", "") } } }
        ecran.onNodeWithTag("profil.saisie.complete").assertIsSelected()
    }

    private companion object {
        const val DELAI_MS = 10_000L
    }
}
