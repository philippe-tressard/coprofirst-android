// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.session.audience

import fr.coprofirst.app.session.ErreurService
import fr.coprofirst.app.session.Installation
import fr.coprofirst.app.session.SessionOuverte
import fr.coprofirst.app.session.SessionsParHote
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

/** La mesure d'audience : défaut fermé, rythme du site, bornes du contrat, résidence quittée. */
@OptIn(ExperimentalCoroutinesApi::class)
class MesureAudienceTest {
    private val sessions = SessionsParHote(OkHttpClient())

    private fun residence(hote: String, contrat: String? = "2.0.0", refus: Boolean? = false) =
        ResidenceMesuree(SessionOuverte(Installation("https://$hote", "Résidence", versionContrat = contrat), sessions), refus)

    /** Ce qui est parti : (hôte, pages), dans l'ordre. */
    private class Releve(var echoue: Boolean = false, var refus: ErreurService = ErreurService.Injoignable) : ServiceAudience {
        val envois = mutableListOf<Pair<String, List<String>>>()

        override suspend fun envoyer(pages: List<String>, session: SessionOuverte) {
            envois += session.installation.url to pages
            if (echoue) throw refus
        }
    }

    /** #162 : la session de SA résidence refusée pour de bon, la file se jette ; celle d'une autre résidence, non. */
    @Test
    fun uneSessionRefuseeJetteLaFileDeSaResidenceSeulement() = runTest {
        val glycines = residence("glycines.exemple.test")
        val m = mesure(Releve()) { glycines }
        m.noter("/tableau-de-bord")
        m.oublier("https://tilleuls.exemple.test")
        assertEquals(listOf("/tableau-de-bord"), m.file)
        m.oublier(glycines.adresse)
        assertTrue(m.file.isEmpty())
    }

    /** #162 : un envoi refusé faute de session n'en fait pas partir d'autres avec elle. */
    @Test
    fun unEnvoiSansSessionArreteLesLotsSuivants() = runTest {
        val releve = Releve(echoue = true, refus = ErreurService.SessionExpiree)
        val m = mesure(releve) { residence("glycines.exemple.test") }
        // Deux lots notés avant que l'envoi parte : le premier refusé, le second ne part pas.
        repeat(2 * LimitesAudience.EVENEMENTS_PAR_LOT) { m.noter("/page/$it") }
        runCurrent()
        assertEquals("le premier lot est parti, le second non", 1, releve.envois.size)
        assertTrue(m.file.isEmpty())
    }

    private fun TestScope.mesure(releve: Releve, ouverte: () -> ResidenceMesuree?) =
        MesureAudience(releve, backgroundScope).apply { courante = ouverte }

    @Test
    fun laFilePartAuBoutDeTrenteSecondes() = runTest {
        val releve = Releve()
        val glycines = residence("glycines.exemple.test")
        val m = mesure(releve) { glycines }
        m.noter("/tableau-de-bord")
        m.noter("/admin#telemetry")
        advanceTimeBy(29.seconds)
        runCurrent()
        assertTrue(releve.envois.isEmpty())
        advanceTimeBy(2.seconds)
        runCurrent()
        assertEquals(listOf(glycines.adresse to listOf("/tableau-de-bord", "/admin#telemetry")), releve.envois)
        assertTrue(m.file.isEmpty())
    }

    @Test
    fun laFilePartDesQuElleAtteintLaBorne() = runTest {
        val releve = Releve()
        val m = mesure(releve) { residence("glycines.exemple.test") }
        repeat(LimitesAudience.EVENEMENTS_PAR_LOT) { m.noter("/page/$it") }
        runCurrent()
        assertEquals(1, releve.envois.size)
        assertEquals(LimitesAudience.EVENEMENTS_PAR_LOT, releve.envois.single().second.size)
    }

    @Test
    fun rienNeSeNoteSansAccordLuNiContratDit() = runTest {
        for (fermee in listOf(
            residence("a.exemple.test", refus = true),
            residence("b.exemple.test", refus = null),
            residence("c.exemple.test", contrat = null),
        )) {
            val releve = Releve()
            val m = mesure(releve) { fermee }
            m.noter("/tableau-de-bord")
            assertTrue(fermee.toString(), m.file.isEmpty())
        }
        val releve = Releve()
        val m = mesure(releve) { null }
        m.noter("/tableau-de-bord")
        assertTrue(m.file.isEmpty())
    }

    @Test
    fun unRefusPoseAvantLEnvoiJetteLaFile() = runTest {
        val releve = Releve()
        var refus = false
        val m = mesure(releve) { residence("glycines.exemple.test", refus = refus) }
        m.noter("/tableau-de-bord")
        refus = true
        m.vider()
        assertTrue(releve.envois.isEmpty())
        assertTrue(m.file.isEmpty())
    }

    @Test
    fun unePageVideOuTropLongueNeSeMetPasDansLeLot() = runTest {
        val releve = Releve()
        val m = mesure(releve) { residence("glycines.exemple.test") }
        m.noter("")
        m.noter(null)
        m.noter("/" + "é".repeat(LimitesAudience.PAGE))
        m.noter("/" + "é".repeat(LimitesAudience.PAGE - 1))
        assertEquals(1, m.file.size)
    }

    @Test
    fun ceQuiResteDeLaResidenceQuitteePartChezElle() = runTest {
        val releve = Releve()
        val glycines = residence("glycines.exemple.test")
        val tilleuls = residence("tilleuls.exemple.test")
        var ouverte = glycines
        val m = mesure(releve) { ouverte }
        m.noter("/tableau-de-bord")
        ouverte = tilleuls
        m.noter("/residence")
        runCurrent()
        assertEquals(listOf(glycines.adresse to listOf("/tableau-de-bord")), releve.envois)
        m.vider()
        assertEquals(tilleuls.adresse to listOf("/residence"), releve.envois.last())
    }

    @Test
    fun unEchecNeSeDitNiNeSeRejoue() = runTest {
        val releve = Releve(echoue = true)
        val m = mesure(releve) { residence("glycines.exemple.test") }
        m.noter("/tableau-de-bord")
        m.vider()
        m.vider()
        assertEquals(1, releve.envois.size)
        assertTrue(m.file.isEmpty())
    }

    @Test
    fun lArrierePlanVideEtOublierJette() = runTest {
        val releve = Releve()
        val m = mesure(releve) { residence("glycines.exemple.test") }
        m.noter("/tableau-de-bord")
        m.viderEnArrierePlan()
        runCurrent()
        assertEquals(1, releve.envois.size)
        m.noter("/residence")
        m.oublier()
        advanceTimeBy(MesureAudience.INTERVALLE * 2)
        runCurrent()
        assertEquals(1, releve.envois.size)
    }

    @Test
    fun lesCheminsSontCeuxDuSite() {
        assertEquals("/sondages", CheminDuSite.page("communaute"))
        assertEquals("/tickets/123", CheminDuSite.affaire(123))
        assertEquals("/sondages/12", CheminDuSite.sondage(12))
        assertEquals("/admin#telemetry", CheminDuSite.onglet("telemetry", "/admin"))
        assertEquals(null, CheminDuSite.page("profil"))
    }
}
