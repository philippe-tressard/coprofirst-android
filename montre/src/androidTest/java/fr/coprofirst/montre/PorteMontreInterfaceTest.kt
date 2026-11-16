// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.montre

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import fr.coprofirst.app.commun.attendreQue
import fr.coprofirst.app.commun.attendreTag
import fr.coprofirst.app.commun.attendreTexte
import fr.coprofirst.app.fonctions.notifications.NotificationRecue
import fr.coprofirst.montre.liaison.CoffreLiaison
import fr.coprofirst.montre.liaison.JetonRefuse
import fr.coprofirst.montre.liaison.LiaisonGardee
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.time.Instant

/** La montre à son ouverture (#207, lot 2b) : reliée, elle lit la cloche ; sinon, elle attend le téléphone. */
@RunWith(AndroidJUnit4::class)
class PorteMontreInterfaceTest {
    @get:Rule
    val ecran = createComposeRule()

    private class Coffre(var gardee: LiaisonGardee?) : CoffreLiaison {
        override fun lire() = gardee
        override fun garder(liaison: LiaisonGardee) { gardee = liaison }
        override fun effacer() { gardee = null }
    }

    private val tilleuls = LiaisonGardee("https://residence.exemple.test", "Les Tilleuls", "jeton", Instant.now().plusSeconds(86_400))
    private val alerte = NotificationRecue(3, "ticket_update", "Votre affaire avance", "", false, false, Instant.now())

    private fun ouvrir(coffre: Coffre, actif: Boolean = true, lire: suspend (LiaisonGardee) -> List<NotificationRecue>) =
        ecran.setContent { MaterialTheme { AppScaffold { PorteMontre(coffre, lire, actif) } } }

    @Test
    fun relieeLaClocheSeLit() {
        ouvrir(Coffre(tilleuls)) { listOf(alerte) }
        ecran.attendreTexte("Les Tilleuls")
        ecran.attendreTag("montre.alerte.3")
    }

    @Test
    fun liaisonEteinte_laMontreAttendLeTelephone() {
        ouvrir(Coffre(tilleuls), actif = false) { listOf(alerte) }
        ecran.attendreTag("montre.nonReliee")
    }

    @Test
    fun jetonRefuse_laLiaisonSEfface() {
        val coffre = Coffre(tilleuls)
        ouvrir(coffre) { throw JetonRefuse() }
        ecran.attendreTag("montre.nonReliee")
        ecran.attendreQue("la liaison effacée") { coffre.gardee == null }
    }

    @Test
    fun residenceInjoignable_seDit() {
        ouvrir(Coffre(tilleuls)) { throw IOException("hors ligne") }
        ecran.attendreTag("montre.injoignable")
    }

    @Test
    fun jetonEchu_laMontreAttendLeTelephone() {
        ouvrir(Coffre(LiaisonGardee(tilleuls.adresse, tilleuls.residence, "jeton", Instant.now().minusSeconds(1)))) { listOf(alerte) }
        ecran.attendreTag("montre.nonReliee")
    }
}
