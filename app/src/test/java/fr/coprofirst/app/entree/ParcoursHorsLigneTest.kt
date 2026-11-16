// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.entree

import fr.coprofirst.api.routes.FluxApi
import fr.coprofirst.app.session.ChiffrementAesGcm
import fr.coprofirst.app.session.CoffreEnMemoire
import fr.coprofirst.app.session.Connexion
import fr.coprofirst.app.session.Decouverte
import fr.coprofirst.app.session.Deverrouillage
import fr.coprofirst.app.session.ErreurService
import fr.coprofirst.app.session.Installation
import fr.coprofirst.app.session.InstallationsConnues
import fr.coprofirst.app.session.LectureDisponible
import fr.coprofirst.app.session.LecturesGardees
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
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Duration
import java.time.Instant
import javax.crypto.spec.SecretKeySpec

/**
 * L'entrée sans réseau (#28), comme `HorsLigneUITests` de l'iOS : la biométrie passée, la
 * dernière lecture s'ouvre en consultation ; trop ancienne, elle est effacée et dite ; le
 * réseau revenu, la session se rouvre sans rien redemander.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ParcoursHorsLigneTest {
    @get:Rule
    val dossier = TemporaryFolder()
    private val serveur = MockWebServer()
    private val ordonnanceur = StandardTestDispatcher()
    private val connues = InstallationsConnues(object : StockageResidences {
        var valeur: String? = null
        override fun lire() = valeur
        override fun ecrire(valeur: String) { this.valeur = valeur }
    }, CoffreEnMemoire())
    private val gardees = SessionsGardeesEnMemoire()
    private var maintenant = Instant.parse("2026-11-10T13:32:00Z")
    private val lectures by lazy {
        LecturesGardees(dossier.root, ChiffrementAesGcm { SecretKeySpec(ByteArray(32), "AES") }, gardees::contient) { maintenant }
    }
    private val residence = Installation("https://connue.exemple.test", "Connue")
    private val hote = "connue.exemple.test"
    /** Sans réseau : une adresse où rien n'écoute ; le réseau revenu : le serveur local. */
    private var base = "http://localhost:1/api"

    @Before
    fun preparer() {
        Dispatchers.setMain(ordonnanceur)
        serveur.start()
        connues.retenir(residence)
        gardees.garder(hote, "r1")
        lectures.garder(FIL.toByteArray(), "application/json", "/api/flux", hote)
    }

    @After
    fun ranger() {
        serveur.close()
        Dispatchers.resetMain()
    }

    private fun lancer(): ParcoursEntree {
        val base = { _: Any -> base }
        val sessions = SessionsParHote(OkHttpClient(), gardees, lectures)
        return ParcoursEntree(
            Decouverte(OkHttpClient(), Dispatchers.Unconfined, base), sessions,
            Connexion(sessions, Dispatchers.Unconfined, base), connues, baseApi = base,
        ) { PreuveHote.Declaree }.also { it.deverrouillage = Deverrouillage { chiffre, _ -> chiffre } }
    }

    private fun repondre(code: Int) =
        serveur.enqueue(MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body("{}").build())

    private fun test(corps: suspend TestScope.() -> Unit) = runTest(ordonnanceur) { corps() }

    @Test
    fun sansReseauLaDerniereLectureSOuvreEnConsultation() = test {
        val parcours = lancer()
        parcours.ouvrirSessionGardee()
        advanceUntilIdle()
        val etat = parcours.etat.value
        assertEquals(EcranEntree.Espace, etat.ecran)
        assertEquals(maintenant, etat.consultation)
        // Le fil gardé se lit, sans réseau.
        val fil = parcours.sessionOuverte()!!.appeler { b, c -> FluxApi(b, c).getFlux() }
        assertEquals(7, fil.delaiRecentJours)
    }

    @Test
    fun uneLectureTropAncienneEstEffaceeEtLEcranLeDit() = test {
        // L'app restée ouverte (au lancement, la purge l'aurait déjà effacée sans rien dire).
        val parcours = lancer()
        maintenant += Duration.ofDays(LecturesGardees.JOURS_MAX + 1)
        parcours.ouvrirSessionGardee()
        advanceUntilIdle()
        assertEquals(EcranEntree.Retour, parcours.etat.value.ecran)
        assertTrue(parcours.etat.value.lecturePerimee)
        assertTrue("la session reste gardée : réessayer au retour du réseau", gardees.contient(hote))
    }

    @Test
    fun leReseauRevenuLaSessionSeRouvreSansRienRedemander() = test {
        val parcours = lancer()
        parcours.ouvrirSessionGardee()
        advanceUntilIdle()
        base = serveur.url("/api").toString()
        repondre(200) // POST /auth/refresh
        parcours.reprendreEnLigne()
        advanceUntilIdle()
        assertNull(parcours.etat.value.consultation)
        val requete = serveur.takeRequest()
        assertEquals("/api/auth/refresh", requete.url.encodedPath)
        assertEquals("refresh_token=r1", requete.headers["Cookie"])
    }

    @Test
    fun auRetourDuReseauUneSessionRefuseeEfaceToutEtMeneALaConnexion() = test {
        val parcours = lancer()
        parcours.ouvrirSessionGardee()
        advanceUntilIdle()
        base = serveur.url("/api").toString()
        repondre(401)
        parcours.reprendreEnLigne()
        advanceUntilIdle()
        val etat = parcours.etat.value
        assertEquals(EcranEntree.Connexion, etat.ecran)
        assertEquals(ErreurService.SessionExpiree, etat.erreurConnexion)
        assertNull(etat.ouverte)
        assertFalse(gardees.contient(hote))
        assertEquals(LectureDisponible.Aucune, lectures.consulter(hote))
    }

    @Test
    fun seDeconnecterHorsLigneNeLaissePlusRienALire() = test {
        val parcours = lancer()
        parcours.ouvrirSessionGardee()
        advanceUntilIdle()
        parcours.deconnecter()
        advanceUntilIdle()
        assertEquals(EcranEntree.Connexion, parcours.etat.value.ecran)
        assertNull(parcours.etat.value.consultation)
        assertFalse(gardees.contient(hote))
        assertEquals(LectureDisponible.Aucune, lectures.consulter(hote))
    }

    private companion object {
        const val FIL = """{"delai_recent_jours": 7, "items": []}"""
    }
}
