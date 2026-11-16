// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.entree

import fr.coprofirst.app.session.CoffreEnMemoire
import fr.coprofirst.app.session.Connexion
import fr.coprofirst.app.session.Decouverte
import fr.coprofirst.app.session.Deverrouillage
import fr.coprofirst.app.session.ErreurService
import fr.coprofirst.app.session.Installation
import fr.coprofirst.app.session.InstallationsConnues
import fr.coprofirst.app.session.PreuveHote
import fr.coprofirst.app.session.ProtectionSession
import fr.coprofirst.app.session.SessionsGardeesEnMemoire
import fr.coprofirst.app.session.SessionsParHote
import fr.coprofirst.app.session.StockageResidences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Le parcours d'une session gardée (#13), sur les choix de l'iOS : la dernière résidence
 * rouverte par la biométrie, le courriel retenu, « Garder la session ? » proposé une fois.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ParcoursSessionGardeeTest {
    private val serveur = MockWebServer()
    private val ordonnanceur = StandardTestDispatcher()
    private val stockage = object : StockageResidences {
        var valeur: String? = null
        override fun lire() = valeur
        override fun ecrire(valeur: String) { this.valeur = valeur }
    }
    private val connues = InstallationsConnues(stockage, CoffreEnMemoire())
    private val gardees = SessionsGardeesEnMemoire(ProtectionSession.Biometrie)
    private val residence = Installation("https://connue.exemple.test", "Connue")
    private val hote = "connue.exemple.test"

    @Before
    fun preparer() {
        Dispatchers.setMain(ordonnanceur)
        serveur.start()
    }

    @After
    fun ranger() {
        serveur.close()
        Dispatchers.resetMain()
    }

    private fun lancer(): ParcoursEntree {
        val base = { _: Any -> serveur.url("/api").toString() }
        val sessions = SessionsParHote(OkHttpClient(), gardees)
        return ParcoursEntree(
            Decouverte(OkHttpClient(), Dispatchers.Unconfined, base), sessions,
            Connexion(sessions, Dispatchers.Unconfined, base), connues, baseApi = base,
        ) { PreuveHote.Declaree }.also { it.deverrouillage = Deverrouillage { chiffre, _ -> chiffre } }
    }

    private fun repondre(code: Int, corps: String = "{}", vararg entetes: Pair<String, String>) {
        val r = MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(corps)
        entetes.forEach { (n, v) -> r.addHeader(n, v) }
        serveur.enqueue(r.build())
    }

    private fun test(corps: suspend TestScope.() -> Unit) = runTest(ordonnanceur) { corps() }

    @Test
    fun uneSessionGardeeRouvreLaDerniereResidenceParLaBiometrie() = test {
        connues.retenir(residence)
        gardees.garder(hote, "r1")
        val parcours = lancer()
        assertEquals(EcranEntree.Retour, parcours.etat.value.ecran)
        repondre(200, "{}", "Set-Cookie" to "refresh_token=r2; Path=/; HttpOnly")
        parcours.ouvrirDOffice()
        advanceUntilIdle()
        assertEquals(EcranEntree.Espace, parcours.etat.value.ecran)
        assertEquals(residence, parcours.etat.value.ouverte)
        // Demandée d'office une seule fois.
        parcours.ouvrirDOffice()
        advanceUntilIdle()
        assertEquals(1, serveur.requestCount)
    }

    @Test
    fun unJetonRefuseMeneAuMotDePasseCourrielPreRempli() = test {
        connues.retenir(residence)
        connues.retenirIdentifiant("a@exemple.test", residence)
        gardees.garder(hote, "r1")
        val parcours = lancer()
        repondre(401)
        parcours.ouvrirSessionGardee()
        advanceUntilIdle()
        val etat = parcours.etat.value
        assertEquals(EcranEntree.Connexion, etat.ecran)
        assertEquals(ErreurService.SessionExpiree, etat.erreurConnexion)
        assertEquals("a@exemple.test", etat.email)
        assertTrue(etat.parMotDePasse)
        assertFalse(gardees.contient(hote))
        // Plus rien de gardé : le retour mène à l'accueil.
        parcours.retour()
        assertEquals(EcranEntree.Accueil, parcours.etat.value.ecran)
    }

    @Test
    fun uneBiometrieAnnuleeLaisseLEcranDeRetourSansRienDire() = test {
        connues.retenir(residence)
        gardees.garder(hote, "r1")
        gardees.refuser = true
        val parcours = lancer()
        parcours.ouvrirSessionGardee()
        advanceUntilIdle()
        assertEquals(EcranEntree.Retour, parcours.etat.value.ecran)
        assertNull(parcours.etat.value.erreurReprise)
        parcours.utiliserMotDePasse()
        assertEquals(EcranEntree.Connexion, parcours.etat.value.ecran)
        // Le retour système ramène à l'écran de retour : la session reste gardée.
        parcours.retour()
        assertEquals(EcranEntree.Retour, parcours.etat.value.ecran)
    }

    @Test
    fun sansSessionGardeeLaDerniereResidenceOuvreLaConnexionPreRemplie() = test {
        connues.retenir(residence)
        connues.retenirIdentifiant("a@exemple.test", residence)
        repondre(200, """{"site_nom": "Connue"}""")
        val parcours = lancer()
        advanceUntilIdle()
        val etat = parcours.etat.value
        assertEquals(EcranEntree.Connexion, etat.ecran)
        assertEquals("connue.exemple.test", etat.saisie)
        assertEquals("Connue", etat.reconnue?.nom)
        assertEquals("a@exemple.test", etat.email)
    }

    @Test
    fun gardeProposeeUneFoisApresLaConnexionPuisActivee() = test {
        connues.retenir(residence)
        repondre(200, """{"site_nom": "Connue"}""")
        val parcours = lancer()
        advanceUntilIdle()
        parcours.saisirEmail("a@exemple.test")
        parcours.saisirMotDePasse("secret")
        repondre(200, "{}", "Set-Cookie" to "refresh_token=r1; Path=/; HttpOnly")
        parcours.seConnecter()
        advanceUntilIdle()
        assertTrue(parcours.etat.value.proposerGarde)
        assertEquals("a@exemple.test", connues.identifiant(residence))
        parcours.activerGarde()
        advanceUntilIdle()
        assertEquals(IssueGarde.Gardee(ProtectionSession.Biometrie), parcours.etat.value.issueGarde)
        assertTrue(gardees.contient(hote))
        parcours.fermerGarde()
        assertFalse(parcours.etat.value.proposerGarde)
        // Se déconnecter efface la session gardée, garde le courriel, et repropose la garde.
        repondre(200)
        parcours.deconnecter()
        advanceUntilIdle()
        assertFalse(gardees.contient(hote))
        assertFalse(connues.gardeProposee(residence))
        assertEquals("a@exemple.test", parcours.etat.value.email)
    }

    @Test
    fun oublierToutEfaceLesSessionsGardeesEtLesCourriels() = test {
        connues.retenir(residence)
        connues.retenirIdentifiant("a@exemple.test", residence)
        gardees.garder(hote, "r1")
        val parcours = lancer()
        parcours.oublierTout()
        advanceUntilIdle()
        assertEquals(EtatEntree(), parcours.etat.value)
        assertFalse(gardees.contient(hote))
        assertNull(connues.identifiant(residence))
    }
}
