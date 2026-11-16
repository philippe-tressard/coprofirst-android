// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.entree.vitrine

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** La relecture de coprofirst.fr (#153), comme l'iOS (`Vitrine.actualiser`) : une fois par lancement réussie, le cache sinon l'embarquée. */
class VitrineDuSiteTest {
    @get:Rule
    val dossier = TemporaryFolder()

    private val embarque = File("src/main/assets/vitrine/contenu.json").readText()
    private val embarquees = checkNotNull(PagesVitrine.lire(embarque))

    /** Le contenu du site, avec un titre changé : ce que la relecture doit montrer. */
    private val du_site = embarque.replaceFirst(embarquees.enSavoirPlus.titre, "Titre relu sur le site")
    private val relues = checkNotNull(PagesVitrine.lire(du_site))

    private fun vitrine(cache: File? = null, vararg reponses: Pair<Int, ByteArray>?): Pair<VitrineDuSite, MutableList<Int>> {
        val appels = mutableListOf<Int>()
        val file = ArrayDeque(reponses.toList())
        return VitrineDuSite(embarquees, lecteur = { appels += 1; file.removeFirstOrNull() }, cache = cache) to appels
    }

    @Test
    fun sansCacheLEmbarqueePuisLeSiteQuiLaRemplaceEtSeGarde() = runTest {
        val cache = File(dossier.root, "contenu-vitrine.json")
        val (v, _) = vitrine(cache, 200 to du_site.toByteArray())
        assertEquals(embarquees, v.pages.value)
        v.actualiser()
        assertEquals(relues, v.pages.value)
        assertTrue("la lecture se garde pour le prochain lancement", cache.isFile)
        val (suivante, _) = vitrine(cache)
        assertEquals("au lancement suivant : le cache", relues, suivante.pages.value)
    }

    @Test
    fun uneSeuleLectureReussieParLancement() = runTest {
        val (v, appels) = vitrine(null, 200 to du_site.toByteArray(), 200 to du_site.toByteArray())
        v.actualiser()
        v.actualiser()
        assertEquals(1, appels.size)
    }

    /** Hors ligne, refusée, trop lourde, incomplète : rien ne change, et la lecture se retente. */
    @Test
    fun unEchecNeChangeRien() = runTest {
        val incomplet = embarque.replace("\"titre\"", "\"x\"")
        for (reponse in listOf(null, 404 to du_site.toByteArray(), 200 to ByteArray(VitrineDuSite.TAILLE_MAX + 1), 200 to incomplet.toByteArray())) {
            val cache = File(dossier.root, "c-${reponse?.first}-${reponse?.second?.size}.json")
            val (v, _) = vitrine(cache, reponse, 200 to du_site.toByteArray())
            v.actualiser()
            assertEquals(reponse?.first.toString(), embarquees, v.pages.value)
            assertFalse(cache.isFile)
            v.actualiser()
            assertEquals("la suivante réussit", relues, v.pages.value)
        }
    }

    /** La lecture elle-même : un GET sans cookie ni jeton, le statut rendu, le corps plafonné. */
    @Test
    fun laLectureNEnvoieRienEtSeBorne() = runTest {
        MockWebServer().use { serveur ->
            serveur.start()
            serveur.enqueue(MockResponse.Builder().code(200).body(Buffer().write(ByteArray(VitrineDuSite.TAILLE_MAX + 50))).build())
            val (statut, corps) = checkNotNull(VitrineDuSite.lire(OkHttpClient(), serveur.url("/contenu.json").toString()))
            val requete = serveur.takeRequest()
            assertEquals("GET", requete.method)
            assertEquals(null, requete.headers["Cookie"])
            assertEquals(null, requete.headers["Authorization"])
            assertEquals(200, statut)
            assertEquals("plafonné à TAILLE_MAX + 1 : refusé ensuite", VitrineDuSite.TAILLE_MAX + 1, corps.size)
        }
    }

    @Test
    fun unCacheIllisibleCedeALEmbarquee() = runTest {
        val cache = dossier.newFile("abime.json").apply { writeText("{ pas du json") }
        assertEquals(embarquees, vitrine(cache).first.pages.value)
    }
}
