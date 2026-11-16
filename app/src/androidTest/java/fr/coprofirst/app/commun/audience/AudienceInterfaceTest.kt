// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.audience

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.coprofirst.app.commun.attendreTexte
import fr.coprofirst.app.commun.toucherTag
import fr.coprofirst.app.commun.toucherTexte
import fr.coprofirst.app.entree.NavigationEntree
import fr.coprofirst.app.entree.ServeurDeTest
import fr.coprofirst.app.entree.entrerDansLaResidence
import fr.coprofirst.app.fonctions.profil.FixturesProfil
import fr.coprofirst.app.session.audience.MesureAudience
import fr.coprofirst.app.session.audience.ServiceAudienceApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList

/**
 * La mesure d'audience branchée (#144), comme l'iOS : deux pages ouvertes, l'app passe en
 * arrière-plan, et le lot part au serveur local avec le canal `android` ; refusée au Profil,
 * rien ne part.
 */
@RunWith(AndroidJUnit4::class)
class AudienceInterfaceTest {
    @get:Rule
    val ecran = createComposeRule()
    private val serveur = ServeurDeTest()
    private val lots = CopyOnWriteArrayList<String>()

    /** Le cycle de vie de l'app, tenu par le test : `ON_STOP` est le passage en arrière-plan. */
    private val appli = object : LifecycleOwner {
        val registre = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registre
    }

    private fun residence(refus: Boolean) = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val chemin = request.url.encodedPath
            return when {
                chemin.endsWith("/config") -> json(200, """{"site_nom": "Les Glycines", "version_contrat": "2.0.0"}""")
                chemin.endsWith("/auth/login") -> json(200, "{}")
                chemin.endsWith("/auth/me") ->
                    json(200, FixturesProfil.MOI.replace("\"opt_out_telemetrie\": false", "\"opt_out_telemetrie\": $refus"))
                chemin.endsWith("/telemetry/collect") -> {
                    lots += request.body?.utf8().orEmpty()
                    MockResponse.Builder().code(204).build()
                }
                else -> json(404, """{"detail": "Introuvable."}""")
            }
        }
    }

    private fun json(code: Int, corps: String) =
        MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(corps).build()

    /** Ouvre la résidence, la première page de la barre, puis le Profil ; et passe en arrière-plan. */
    private fun deuxPagesPuisArrierePlan(refus: Boolean) {
        serveur.serveur.dispatcher = residence(refus)
        val mesure = MesureAudience(ServiceAudienceApi, CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate))
        ecran.runOnUiThread { appli.registre.currentState = Lifecycle.State.RESUMED }
        ecran.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides appli) {
                NavigationEntree(serveur.apparence(), serveur.parcours(), mesure)
            }
        }
        ecran.entrerDansLaResidence("residence.exemple.test", "Les Glycines")
        ecran.attendreTexte("Plus")
        ecran.toucherTexte("Plus")
        ecran.toucherTag("plus.profil")
        ecran.attendreTexte("Camille EXEMPLE")
        ecran.runOnUiThread { appli.registre.currentState = Lifecycle.State.CREATED }
    }

    @After
    fun fermer() = serveur.close()

    @Test
    fun lesPagesVuesPartentEnArrierePlanAvecLeCanalAndroid() {
        deuxPagesPuisArrierePlan(refus = false)
        ecran.waitUntil(DELAI_MS) { lots.isNotEmpty() }
        val lot = lots.joinToString()
        assertTrue(lot, "\"canal\":\"android\"" in lot.replace(" ", ""))
        assertTrue(lot, "\"/profil\"" in lot)
        assertTrue(lot, "\"view\"" in lot)
    }

    @Test
    fun refuseeAuProfilRienNePart() {
        deuxPagesPuisArrierePlan(refus = true)
        // Le même délai que le cas précédent : un envoi aurait eu le temps de partir.
        Thread.sleep(ATTENTE_MS)
        ecran.waitForIdle()
        assertTrue(lots.joinToString(), lots.isEmpty())
    }

    private companion object {
        const val DELAI_MS = 10_000L
        const val ATTENTE_MS = 3_000L
    }
}
