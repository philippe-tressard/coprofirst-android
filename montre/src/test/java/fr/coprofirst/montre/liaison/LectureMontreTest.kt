// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.montre.liaison

import fr.coprofirst.app.session.Chiffrement
import fr.coprofirst.app.session.LecturesGardees
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant

/** La lecture de la montre (#207, lot 6) : en ligne elle garde, hors ligne elle rend la dernière, datée. */
class LectureMontreTest {
    @get:Rule
    val dossier = TemporaryFolder()

    private val serveur = MockWebServer().apply { start() }
    private val adresse = "https://residence.exemple.test"
    private var maintenant = Instant.parse("2026-11-17T13:32:00Z")

    private class Coffre(var gardee: LiaisonGardee?) : CoffreLiaison {
        override fun lire() = gardee
        override fun garder(liaison: LiaisonGardee) { gardee = liaison }
        override fun effacer() { gardee = null }
    }

    /** Le chiffrement est éprouvé avec le téléphone ; ici, les octets passent tels quels. */
    private object EnClair : Chiffrement {
        override fun chiffrer(clair: ByteArray) = clair
        override fun dechiffrer(chiffre: ByteArray) = chiffre
    }

    private val coffre = Coffre(LiaisonGardee(adresse, "Les Tilleuls", "jeton", Instant.parse("2026-12-17T10:00:00Z")))
    private val lectures by lazy {
        LecturesGardees(dossier.newFolder(), EnClair, sessionGardee = { it == adresse }, horloge = { maintenant })
    }

    /** La vraie lecture (client généré, porte des lectures gardées), dirigée vers le serveur de test. */
    private fun lecture(actif: Boolean = true) = LectureMontre(
        coffre,
        lectures,
        { _, jeton, gardien -> ClocheParJeton(serveur.url("/api").toString(), jeton, gardien = gardien).lire() },
        actif,
    )

    @After
    fun arreter() = serveur.close()

    private fun repondre(code: Int, corps: String) = serveur.enqueue(
        MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(corps).build(),
    )

    private val deux = """[
        {"id": 1, "type": "ticket_update", "titre": "A", "corps": "", "lue": false, "urgente": true, "cree_le": "2026-11-17T08:00:00Z"},
        {"id": 2, "type": "system", "titre": "B", "corps": "", "lue": true, "urgente": false, "cree_le": "2026-11-16T08:00:00Z"}]"""

    @Test
    fun enLigne_laClocheSeLitEtSeGarde() = runTest {
        repondre(200, deux)
        val etat = lecture().lire() as EtatLecture.Lue
        assertEquals("Les Tilleuls", etat.residence)
        assertEquals(listOf(1), etat.cloche.nonLues.map { it.id })
        assertTrue(lectures.consulter(adresse) is fr.coprofirst.app.session.LectureDisponible.Gardee)
    }

    @Test
    fun horsLigne_laDerniereLectureGardeeDatee() = runTest {
        repondre(200, deux)
        lecture().lire()
        serveur.close()
        maintenant = maintenant.plusSeconds(3_600)
        val etat = lecture().lire() as EtatLecture.HorsLigne
        assertEquals(listOf(1), etat.cloche.nonLues.map { it.id })
        assertEquals(Instant.parse("2026-11-17T13:32:00Z"), etat.aJourAu)
    }

    @Test
    fun horsLigneSansLectureGardee_injoignable() = runTest {
        serveur.close()
        assertTrue(lecture().lire() is EtatLecture.Injoignable)
    }

    @Test
    fun jetonRefuse_liaisonEtLecturesEffacees() = runTest {
        repondre(200, deux)
        lecture().lire()
        repondre(401, """{"detail": "Non authentifié"}""")
        assertEquals(EtatLecture.NonReliee, lecture().lire())
        assertNull(coffre.gardee)
        assertTrue(lectures.consulter(adresse) is fr.coprofirst.app.session.LectureDisponible.Aucune)
    }

    @Test
    fun liaisonEteinte_rienNeSeLit() = runTest {
        assertEquals(EtatLecture.NonReliee, lecture(actif = false).lire())
        assertEquals(0, serveur.requestCount)
    }
}
