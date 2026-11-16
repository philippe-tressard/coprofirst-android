// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.entree

import fr.coprofirst.api.routes.ConfigApi
import fr.coprofirst.app.session.CoffreEnMemoire
import fr.coprofirst.app.session.Connexion
import fr.coprofirst.app.session.Decouverte
import fr.coprofirst.app.session.Deverrouillage
import fr.coprofirst.app.session.ErreurService
import fr.coprofirst.app.session.Installation
import fr.coprofirst.app.session.InstallationsConnues
import fr.coprofirst.app.session.PreuveHote
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
 * La session refusée en cours d'usage (#201), comme `SessionRefuseeTests` de l'iOS
 * (coprofirst-ios#248) : le serveur refuse aussi le renouvellement — l'app quitte la
 * résidence UNE fois et ouvre la connexion pré-remplie, la raison dite ; rien de la
 * session ne reste sur l'appareil.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ParcoursSessionRefuseeTest {
    private val serveur = MockWebServer()
    private val ordonnanceur = StandardTestDispatcher()
    private val connues = InstallationsConnues(object : StockageResidences {
        var valeur: String? = null
        override fun lire() = valeur
        override fun ecrire(valeur: String) { this.valeur = valeur }
    }, CoffreEnMemoire())
    private val gardees = SessionsGardeesEnMemoire()
    private val residence = Installation("https://connue.exemple.test", "Connue")
    private val hote = "connue.exemple.test"
    /** Les résidences dont la session s'est close (#162). */
    private val closes = mutableListOf<Installation>()
    private var piecesEffacees = 0

    @Before
    fun preparer() {
        Dispatchers.setMain(ordonnanceur)
        serveur.start()
        connues.retenir(residence)
        connues.retenirIdentifiant("a@exemple.test", residence)
        gardees.garder(hote, "r1")
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
            Connexion(sessions, Dispatchers.Unconfined, base), connues, effacerLesPieces = { piecesEffacees += 1 },
            baseApi = base, apresSortie = { closes += it },
        ) { PreuveHote.Declaree }.also { it.deverrouillage = Deverrouillage { chiffre, _ -> chiffre } }
    }

    private fun repondre(code: Int, vararg entetes: Pair<String, String>) {
        val r = MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body("{}")
        entetes.forEach { (n, v) -> r.addHeader(n, v) }
        serveur.enqueue(r.build())
    }

    private fun test(corps: suspend TestScope.() -> Unit) = runTest(ordonnanceur) { corps() }

    /** La dernière résidence rouverte par la biométrie : l'espace, sa session en ligne. */
    private suspend fun TestScope.ouvrir(): ParcoursEntree {
        val parcours = lancer()
        repondre(200, "Set-Cookie" to "refresh_token=r2; Path=/; HttpOnly")
        parcours.ouvrirSessionGardee()
        advanceUntilIdle()
        assertEquals(EcranEntree.Espace, parcours.etat.value.ecran)
        return parcours
    }

    /** Une lecture d'un écran ; son erreur, s'il y en a une. */
    private suspend fun ParcoursEntree.lire(): Throwable? =
        runCatching { sessionOuverte()!!.appeler { b, c -> ConfigApi(b, c).getConfig() } }.exceptionOrNull()

    @Test
    fun unRenouvellementRefuseRameneALaConnexionPreRemplie() = test {
        val parcours = ouvrir()
        repondre(401) // la lecture
        repondre(401) // POST /auth/refresh, refusé à son tour
        assertEquals(ErreurService.SessionExpiree, parcours.lire())
        advanceUntilIdle()
        val etat = parcours.etat.value
        assertEquals(EcranEntree.Connexion, etat.ecran)
        assertNull(etat.ouverte)
        assertEquals(ErreurService.SessionExpiree, etat.erreurConnexion)
        assertTrue(etat.parMotDePasse)
        assertEquals("connue.exemple.test", etat.saisie)
        assertEquals("a@exemple.test", etat.email)
        assertFalse("plus d'empreinte proposée sur une session morte", gardees.contient(hote))
        assertEquals(listOf(residence), closes)
        assertEquals(1, piecesEffacees)
    }

    @Test
    fun plusieursEcransRefusesEnsembleNeRamenentQuUneFois() = test {
        val parcours = ouvrir()
        val premier = parcours.sessionOuverte()!!
        val second = parcours.sessionOuverte()!!
        repeat(4) { repondre(401) }
        runCatching { premier.appeler { b, c -> ConfigApi(b, c).getConfig() } }
        runCatching { second.appeler { b, c -> ConfigApi(b, c).getConfig() } }
        advanceUntilIdle()
        assertEquals(EcranEntree.Connexion, parcours.etat.value.ecran)
        assertEquals(listOf(residence), closes)
        assertEquals(1, piecesEffacees)
    }

    @Test
    fun unRefusAuRejeuApresUnRenouvellementAccepteRameneAussi() = test {
        val parcours = ouvrir()
        repondre(401)
        repondre(200, "Set-Cookie" to "refresh_token=r3; Path=/; HttpOnly")
        repondre(401) // la lecture rejouée, refusée encore
        assertEquals(ErreurService.SessionExpiree, parcours.lire())
        advanceUntilIdle()
        assertEquals(EcranEntree.Connexion, parcours.etat.value.ecran)
        assertFalse(gardees.contient(hote))
    }

    @Test
    fun unRefusQuiNEstPasUn401LaisseLaResidenceOuverte() = test {
        val parcours = ouvrir()
        repondre(403)
        parcours.lire()
        advanceUntilIdle()
        assertEquals(EcranEntree.Espace, parcours.etat.value.ecran)
        assertEquals(residence, parcours.etat.value.ouverte)
        assertTrue(gardees.contient(hote))
        assertTrue(closes.isEmpty())
    }

    @Test
    fun unRenouvellementSansReseauLaisseLaResidenceOuverte() = test {
        val parcours = ouvrir()
        repondre(401)
        serveur.close() // le renouvellement ne joint plus personne
        assertEquals(ErreurService.Injoignable, parcours.lire())
        advanceUntilIdle()
        assertEquals(EcranEntree.Espace, parcours.etat.value.ecran)
        assertTrue("sans réponse du serveur, rien ne s'efface", gardees.contient(hote))
        assertTrue(closes.isEmpty())
    }
}
