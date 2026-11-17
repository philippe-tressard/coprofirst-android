// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.montre

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import fr.coprofirst.app.commun.amenerTag
import fr.coprofirst.app.commun.attendreTag
import fr.coprofirst.app.commun.attendreTexte
import fr.coprofirst.app.commun.toucherTag
import fr.coprofirst.app.fonctions.notifications.NotificationRecue
import fr.coprofirst.montre.cloche.Cloche
import fr.coprofirst.montre.cloche.LectureCloche
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * La cloche de la montre (#207, périmètre du 16/11/2026, coprofirst-ios#226) : les non lues
 * d'abord, les urgentes devant ; « Déjà lues » repliée ; le détail au toucher ; les états vides.
 */
@RunWith(AndroidJUnit4::class)
class ClocheInterfaceTest {
    @get:Rule
    val ecran = createComposeRule()

    private val maintenant = Instant.now()

    private fun alerte(id: Int, titre: String, lue: Boolean = false, urgente: Boolean = false, lien: String? = null, corps: String = "") =
        NotificationRecue(id, "system", titre, corps, lue, urgente, maintenant.minusSeconds(id * 600L), lien)

    private fun montrer(etat: EtatMontre, alertes: List<NotificationRecue> = emptyList()) = ecran.setContent {
        MaterialTheme { AppScaffold { EcranMontre(etat, LectureCloche.Lue(Cloche.de(alertes))) } }
    }

    private val reliee = EtatMontre.Reliee("https://residence.exemple.test", "Les Tilleuls")

    @Test
    fun nonRelieeDitDeRelierParLeTelephone() {
        montrer(EtatMontre.NonReliee)
        ecran.attendreTexte("Ouvrez CoproFirst sur le téléphone pour relier la montre")
    }

    @Test
    fun laResidenceEnTeteEtLesUrgentesDevant() {
        montrer(reliee, listOf(alerte(1, "Ascenseur réparé"), alerte(2, "Fuite au sous-sol", urgente = true)))
        ecran.attendreTag("montre.residence")
        ecran.onNodeWithText("Les Tilleuls").assertIsDisplayed()
        val urgente = ecran.amenerTag("montre.alerte.2").fetchSemanticsNode().boundsInRoot.top
        val ordinaire = ecran.amenerTag("montre.alerte.1").fetchSemanticsNode().boundsInRoot.top
        assertTrue("l'urgente (servie après) passe devant", urgente < ordinaire)
        ecran.onNodeWithText("Urgent").assertIsDisplayed()
    }

    @Test
    fun dejaLuesRepliee_seDeplieAuToucher() {
        montrer(reliee, listOf(alerte(1, "Nouvelle"), alerte(2, "Ancienne", lue = true)))
        ecran.attendreTag("montre.alerte.1")
        ecran.onNodeWithTag("montre.alerte.2").assertDoesNotExist()
        ecran.toucherTag("montre.dejaLues")
        ecran.amenerTag("montre.alerte.2").assertIsDisplayed()
    }

    @Test
    fun aucuneAlerteNonLue() {
        montrer(reliee, listOf(alerte(1, "Ancienne", lue = true)))
        ecran.attendreTag("montre.aucuneAlerte")
        ecran.onNodeWithText("Aucune alerte").assertIsDisplayed()
        assertTrue(ecran.onAllNodesWithTag("montre.alerte.1").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun leToucherOuvreLeDetail_sansRienMarquerCommeLu() {
        montrer(reliee, listOf(alerte(7, "Coupure d'eau jeudi")))
        ecran.toucherTag("montre.alerte.7")
        ecran.attendreTag("montre.detail.7")
        ecran.onNodeWithText("Coupure d'eau jeudi").assertIsDisplayed()
    }

    @Test
    fun continuerSurLeTelephone_seulementQuandLAlerteViseUnePage() {
        montrer(reliee, listOf(alerte(7, "Votre affaire avance", lien = "/tickets/12"), alerte(8, "Bienvenue")))
        ecran.toucherTag("montre.alerte.7")
        ecran.attendreTag("montre.detail.7")
        ecran.amenerTag("montre.continuer")
        ecran.onNodeWithText("Continuer sur le téléphone").assertIsDisplayed()
    }

    @Test
    fun sansLien_pasDeContinuer() {
        montrer(reliee, listOf(alerte(8, "Bienvenue")))
        ecran.toucherTag("montre.alerte.8")
        ecran.attendreTag("montre.detail.8")
        ecran.onNodeWithTag("montre.continuer").assertDoesNotExist()
    }

    @Test
    fun leDetailMontreLeCorpsEnEntier() {
        montrer(reliee, listOf(alerte(9, "Coupure d'eau", corps = "<p>L'eau sera coupée <strong>jeudi</strong> de 8 h à 12 h.</p>")))
        ecran.toucherTag("montre.alerte.9")
        ecran.amenerTag("montre.corps")
        ecran.onNodeWithText("L'eau sera coupée jeudi de 8 h à 12 h.").assertIsDisplayed()
    }
}
