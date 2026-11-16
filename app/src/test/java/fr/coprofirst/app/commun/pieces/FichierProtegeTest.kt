// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.pieces

import fr.coprofirst.app.session.AdresseInstallation
import fr.coprofirst.app.session.ClientHttp
import fr.coprofirst.app.session.Connexion
import fr.coprofirst.app.session.ErreurService
import fr.coprofirst.app.session.Installation
import fr.coprofirst.app.session.SessionOuverte
import fr.coprofirst.app.session.SessionsParHote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** LA porte des fichiers protégés : la session de la résidence, son hôte et lui seul. */
class FichierProtegeTest {
    @get:Rule
    val dossier = TemporaryFolder()

    private val serveur = MockWebServer().apply { start() }
    private val sessions = SessionsParHote(ClientHttp.partage)
    private val base = { _: AdresseInstallation -> serveur.url("/api").toString() }
    private val residence = Installation("https://residence.exemple.test", "Les Glycines")
    private val session = SessionOuverte(residence, sessions, Dispatchers.Unconfined, base)

    @After
    fun arreter() = serveur.close()

    private fun servir(code: Int, corps: String = "", vararg entetes: Pair<String, String>) {
        val r = MockResponse.Builder().code(code).body(corps)
        entetes.forEach { (n, v) -> r.addHeader(n, v) }
        serveur.enqueue(r.build())
    }

    private suspend fun echec(chemin: String): ErreurService = try {
        session.fichierProtege(chemin)
        error("acceptée")
    } catch (e: ErreurService) {
        e
    }

    @Test
    fun unFichierSeLitParLaSessionDeLHote() = runTest {
        servir(200, "{}", "Set-Cookie" to "access_token=jeton; Path=/; HttpOnly", "Content-Type" to "application/json")
        Connexion(sessions, Dispatchers.Unconfined, base).connecter(residence, "a@exemple.test", "x")
        serveur.takeRequest()
        servir(200, "JPEG", "Content-Type" to "image/jpeg")
        assertArrayEquals("JPEG".toByteArray(), session.fichierProtege("/uploads/tickets/a.jpg"))
        val requete = serveur.takeRequest()
        // À la racine de l'hôte, pas sous /api ; avec le cookie de la session.
        assertEquals("/uploads/tickets/a.jpg", requete.url.encodedPath)
        assertEquals("access_token=jeton", requete.headers["Cookie"])
    }

    @Test
    fun uneUrlEntiereDuMemeHoteSeLit() = runTest {
        servir(200, "%PDF")
        val entiere = serveur.url("/uploads/devis.pdf").toString()
        assertArrayEquals("%PDF".toByteArray(), session.fichierProtege(entiere))
    }

    @Test
    fun unAutreHoteEstRefuseSansAucuneRequete() = runTest {
        val port = serveur.port
        for (ailleurs in listOf(
            "https://ailleurs.test/uploads/a.jpg",
            "//ailleurs.test/uploads/a.jpg",
            "https://localhost:$port/uploads/a.jpg", // même hôte, autre schéma
            "http://localhost:${port + 1}/uploads/a.jpg", // même hôte, autre port
            "http://moi:secret@localhost:$port/uploads/a.jpg", // des identifiants dans l'adresse
        )) {
            assertEquals(ailleurs, ErreurService.Inattendue, echec(ailleurs))
        }
        assertEquals("aucune requête n'est partie", 0, serveur.requestCount)
        assertNull(FichierProtege.adresse("pas une adresse", "/uploads/a.jpg"))
    }

    @Test
    fun chaqueRefusDitSaPhrase() = runTest {
        servir(401)
        servir(401) // POST /auth/refresh refusé (#13)
        assertEquals(ErreurService.SessionExpiree, echec("/uploads/a.jpg"))
        servir(404)
        assertEquals(ErreurService.Introuvable, echec("/uploads/a.jpg"))
        servir(403)
        assertEquals(ErreurService.Introuvable, echec("/uploads/a.jpg"))
        servir(500, "<html>")
        assertEquals(ErreurService.Inattendue, echec("/uploads/a.jpg"))
    }

    @Test
    fun uneRedirectionNEstPasSuivie() = runTest {
        servir(302, "", "Location" to "https://ailleurs.test/vol.jpg")
        assertEquals(ErreurService.Inattendue, echec("/uploads/a.jpg"))
        assertEquals(1, serveur.requestCount)
    }

    @Test
    fun unFichierTropLourdNeSeLitPas() = runTest {
        serveur.enqueue(MockResponse.Builder().code(200).body(Buffer().write(ByteArray((FichierProtege.PLAFOND + 1).toInt()))).build())
        assertEquals(ErreurService.Inattendue, echec("/uploads/enorme.pdf"))
    }

    @Test
    fun unePieceOuverteSEcritSousSonNomLisibleEtNeSeLitQuUneFois() = runTest {
        val locales = PiecesLocales(dossier.root, Dispatchers.Unconfined)
        servir(200, "%PDF-1.7")
        servir(200, "JPEG")
        val devis = locales.piece("/uploads/tickets/0123456789abcdef0123456789abcdef_Devis%20plombier.pdf", session)
        val photo = locales.piece("/uploads/tickets/41-1.jpg", session)
        assertEquals("Devis plombier.pdf", devis.name)
        assertEquals("41-1.jpg", photo.name)
        assertEquals("%PDF-1.7", devis.readText())
        assertTrue("dans le cache des pièces", devis.canonicalPath.startsWith(dossier.root.canonicalPath))
        // Rouverte : sans réseau.
        assertEquals(devis, locales.piece("/uploads/tickets/0123456789abcdef0123456789abcdef_Devis%20plombier.pdf", session))
        assertEquals(2, serveur.requestCount)
        // Deux pièces de même nom, d'adresses différentes, ne s'écrasent pas.
        servir(200, "%PDF-autre")
        val autre = locales.piece("/uploads/autre/0123456789abcdef0123456789abcdef_Devis%20plombier.pdf", session)
        assertEquals(devis.name, autre.name)
        assertNotEquals(devis, autre)
        assertEquals("%PDF-1.7", devis.readText())
        // Effacées : plus rien sur le disque, et la suivante se relit.
        locales.purger()
        assertTrue(dossier.root.listFiles().isNullOrEmpty())
        servir(200, "JPEG")
        locales.piece("/uploads/tickets/41-1.jpg", session)
        assertEquals(4, serveur.requestCount)
    }

    @Test
    fun unDocumentDuFilPasseParLeClientGenere() = runTest {
        val locales = PiecesLocales(dossier.root, Dispatchers.Unconfined)
        servir(200, "%PDF-1.7", "Content-Type" to "application/octet-stream",
            "Content-Disposition" to "attachment; filename=\"pv.pdf\"")
        val fichier = locales.document(7, "../PV AG 2026.pdf", session)
        assertEquals("-PV AG 2026.pdf", fichier.name)
        assertEquals(File(File(dossier.root, "documents"), "7"), fichier.parentFile)
        assertEquals("%PDF-1.7", fichier.readText())
        val requete = serveur.takeRequest()
        assertTrue(requete.url.encodedPath, requete.url.encodedPath.startsWith("/api/documents/7/"))
        // Sans nom servi : le nom par défaut.
        servir(200, "x", "Content-Type" to "application/octet-stream")
        assertEquals("document-8", locales.document(8, null, session).name)
    }

    @Test
    fun unDocumentRefuseSeDitIntrouvable() = runTest {
        val locales = PiecesLocales(dossier.root, Dispatchers.Unconfined)
        servir(404, """{"detail": "Document introuvable"}""", "Content-Type" to "application/json")
        val erreur = try {
            locales.document(9, "x.pdf", session)
            error("accepté")
        } catch (e: ErreurService) {
            e
        }
        assertEquals(ErreurService.Refus("Document introuvable", 404), erreur)
    }
}
