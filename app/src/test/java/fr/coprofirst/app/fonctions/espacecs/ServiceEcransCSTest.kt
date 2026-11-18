// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.espacecs

import fr.coprofirst.app.commun.perimetres.ChoixDePerimetres
import fr.coprofirst.app.session.ErreurService
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** « Écrans du hall » (#283), par le client GÉNÉRÉ, contre la résidence simulée — comme l'iOS (`EcransHallTests`). */
class ServiceEcransCSTest {
    private val banc = ServeurCS()
    private val service = ServiceEcransCSApi(banc.session)
    private val saisie = SaisieAppairage(" bcdf-ghjk ", " Hall du bâtiment 2 ", ChoixDePerimetres(setOf("copropriete"), unique = true))

    @After
    fun arreter() = banc.close()

    @Test
    fun laListeDitLeLieuLaLectureEtLEcheance() = runTest {
        val ecrans = service.ecrans()
        assertEquals(listOf(7, 8), ecrans.map { it.id })
        assertEquals("Bât. A", ecrans[0].lieu)
        assertFalse(ecrans[0].echu)
        // Le périmètre par défaut se tait ; jamais lu : `null`.
        assertNull(ecrans[1].lieu)
        assertNull(ecrans[1].vuLe)
        assertTrue(ecrans[1].echu)
    }

    @Test
    fun appairerEnvoieLesHuitLettresLeNomEtLePerimetre() = runTest {
        assertEquals(EcranAppaire("Hall du bâtiment 2", ""), service.appairer(saisie))
        val envoi = banc.residence.recues.single { it.startsWith("POST /api/ecrans ") }
        assertEquals("POST /api/ecrans {\"code_court\":\"BCDFGHJK\",\"nom\":\"Hall du bâtiment 2\",\"perimetre\":\"copropriete\"}", envoi)
    }

    @Test
    fun unCodeInconnuDitLaPhraseDuServeur() = runTest {
        try {
            service.appairer(saisie.copy(code = "ZZZZ-ZZZZ"))
            fail("le serveur refuse un code que nul écran n'affiche")
        } catch (e: ErreurService.Refus) {
            assertTrue(e.phraseServie.startsWith("Ce code ne correspond à aucun écran"))
        }
    }

    @Test
    fun unEcranRetireQuitteLaListe() = runTest {
        service.retirer(8)
        assertTrue(banc.residence.recues.any { it.startsWith("DELETE /api/ecrans/8") })
        assertEquals(listOf(7), service.ecrans().map { it.id })
    }

    /** La règle `complet` du site : huit lettres (tiret, espaces et casse libres), un nom, un périmètre. */
    @Test
    fun laSaisieEstCompleteCommeSurLeSite() {
        assertEquals("BCDFGHJK", saisie.lettres)
        assertTrue(saisie.complete)
        assertFalse(saisie.copy(code = "BCDF-GHJ").complete)
        assertFalse(saisie.copy(nom = "  ").complete)
        assertFalse(saisie.copy(perimetres = ChoixDePerimetres(unique = true)).complete)
    }
}
