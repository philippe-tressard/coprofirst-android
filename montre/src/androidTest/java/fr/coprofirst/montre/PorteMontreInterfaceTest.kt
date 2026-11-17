// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.montre

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.wear.compose.material3.AppScaffold
import fr.coprofirst.app.commun.attendreTag
import fr.coprofirst.app.commun.attendreTexte
import fr.coprofirst.app.fonctions.notifications.NotificationRecue
import fr.coprofirst.montre.cloche.Cloche
import fr.coprofirst.montre.liaison.EtatLecture
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/** La montre à son ouverture (#207, lots 2b et 6) : la cloche, la dernière gardée hors ligne, ou le téléphone. */
@RunWith(AndroidJUnit4::class)
class PorteMontreInterfaceTest {
    @get:Rule
    val ecran = createComposeRule()

    private val adresse = "https://residence.exemple.test"
    private val reliee = EtatMontre.Reliee(adresse, "Les Tilleuls")
    private val cloche = Cloche.de(listOf(NotificationRecue(3, "ticket_update", "Votre affaire avance", "", false, false, Instant.now())))

    private fun ouvrir(depart: EtatMontre, lecture: EtatLecture) =
        ecran.setContent { ThemeMontre { AppScaffold { PorteMontre(depart) { lecture } } } }

    @Test
    fun relieeLaClocheSeLit() {
        ouvrir(reliee, EtatLecture.Lue("Les Tilleuls", adresse, cloche))
        ecran.attendreTexte("Les Tilleuls")
        ecran.attendreTag("montre.alerte.3")
    }

    @Test
    fun horsLigne_laDerniereLectureGardeeDatee() {
        ouvrir(reliee, EtatLecture.HorsLigne("Les Tilleuls", adresse, cloche, Instant.parse("2026-11-17T13:32:00Z")))
        ecran.attendreTag("montre.horsLigne")
        ecran.attendreTag("montre.alerte.3")
    }

    @Test
    fun jetonRefuse_laMontreAttendLeTelephone() {
        ouvrir(reliee, EtatLecture.NonReliee)
        ecran.attendreTag("montre.nonReliee")
    }

    @Test
    fun residenceInjoignableSansLectureGardee_seDit() {
        ouvrir(reliee, EtatLecture.Injoignable("Les Tilleuls", adresse))
        ecran.attendreTag("montre.injoignable")
    }

    @Test
    fun pasReliee_laMontreAttendLeTelephone() {
        ouvrir(EtatMontre.NonReliee, EtatLecture.Lue("Les Tilleuls", adresse, cloche))
        ecran.attendreTag("montre.nonReliee")
    }
}
