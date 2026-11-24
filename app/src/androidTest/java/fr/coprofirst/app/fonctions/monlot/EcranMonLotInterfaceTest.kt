// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.monlot

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.coprofirst.app.ThemeCoproFirst
import fr.coprofirst.app.commun.attendreTexte
import fr.coprofirst.app.commun.residenceSimulee
import fr.coprofirst.app.commun.toucherTag
import fr.coprofirst.app.commun.toucherTexte
import fr.coprofirst.app.entree.ServeurDeTest
import fr.coprofirst.app.navigation.ActionsDeclarees
import fr.coprofirst.app.navigation.BarreDuHaut
import fr.coprofirst.app.navigation.DroitsOnglet
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * « Mes lots & accès » (#18) à l'écran, comme l'iOS (`AccesUITests`) : chaque onglet
 * s'ouvre, et les gestes d'accès aboutissent — contre la résidence simulée, servie en
 * HTTPS à l'app par son vrai client. Les repères sont les identifiants de l'iOS.
 */
@RunWith(AndroidJUnit4::class)
class EcranMonLotInterfaceTest {
    @get:Rule
    val ecran = createComposeRule()
    private val residence = ResidenceSimulee()
    private val serveur = ServeurDeTest().apply {
        serveur.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val (code, corps) = residence.repondre(request.method, request.url.encodedPath, request.body?.utf8().orEmpty())
                return MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(corps).build()
            }
        }
    }

    @Before
    fun ouvrir() {
        val session = serveur.session(residenceSimulee("https://residence.test", "Les Glycines"))
        val barre = BarreDuHaut()
        ecran.setContent {
            ThemeCoproFirst {
                // La barre du haut de la résidence, réduite à ses actions : le « + » s'y pose.
                Column {
                    Row { ActionsDeclarees(barre) }
                    EcranMonLot(ServiceMonLotApi(session), barre, DroitsOnglet())
                }
            }
        }
    }

    @After
    fun fermer() = serveur.close()

    @Test
    fun lesQuatreOngletsSOuvrent() {
        ecran.attendreTexte("Caractéristiques")
        ecran.toucherTag(ReperesMonLot.lot(417))
        ecran.attendreTexte("Sous-sol A")
        ecran.toucherTag(ReperesMonLot.onglet("badges"))
        ecran.attendreTexte("A4F21C07")
        ecran.toucherTag(ReperesMonLot.onglet("telecommandes"))
        ecran.attendreTexte("TC-2048")
        // Le compte simulé est au conseil syndical : la gestion locative lui est ouverte.
        ecran.toucherTag(ReperesMonLot.onglet("location"))
        ecran.attendreTexte("Locataire DU LOT 15")
        ecran.toucherTexte("📁 Archives", substring = true)
        ecran.attendreTexte("Ancien locataire DU LOT 15")
    }

    @Test
    fun signalerPerduPuisDeclarer() {
        ecran.attendreTexte("Caractéristiques")
        ecran.toucherTag(ReperesMonLot.onglet("badges"))
        ecran.toucherTag(ReperesMonLot.perdu(12))
        ecran.toucherTag(ReperesMonLot.PERDU_CONFIRMER)
        ecran.attendreTexte("Signalement enregistré")
        ecran.waitUntil(DELAI_MS) { ecran.onAllNodes(hasTestTag(ReperesMonLot.perdu(12))).fetchSemanticsNodes().isEmpty() }

        ecran.toucherTag(ReperesMonLot.GESTES)
        ecran.toucherTag(ReperesMonLot.DECLARER)
        ecran.toucherTag(ReperesMonLot.DECLARER_CODE)
        ecran.onNodeWithTag(ReperesMonLot.DECLARER_CODE).performTextInput("TC-7777")
        ecran.toucherTag(ReperesMonLot.ENREGISTRER)
        ecran.attendreTexte("Accès enregistré")
        ecran.toucherTag(ReperesMonLot.onglet("telecommandes"))
        ecran.attendreTexte("TC-7777") // la liste est relue
    }

    @Test
    fun nouvelAccesNeProposeQueSesLots() {
        ecran.attendreTexte("Caractéristiques")
        ecran.toucherTag(ReperesMonLot.onglet("badges"))
        ecran.toucherTag(ReperesMonLot.GESTES)
        ecran.toucherTag(ReperesMonLot.DEMANDER)
        ecran.attendreTexte("Nouvelle demande d'accès")
        ecran.toucherTag(ReperesMonLot.ENREGISTRER)
        ecran.attendreTexte("Sélectionnez un lot") // un lot est exigé
        ecran.attendreTexte("Lot 450")
        ecran.toucherTexte("Lot 15")
        ecran.toucherTag(ReperesMonLot.ENREGISTRER)
        ecran.attendreTexte("Demande envoyée au conseil syndical")
    }

    private companion object {
        const val DELAI_MS = 10_000L
    }
}
