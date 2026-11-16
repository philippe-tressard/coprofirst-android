// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.apropos

import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.coprofirst.app.commun.amenerTag
import fr.coprofirst.app.commun.attendreTexte
import fr.coprofirst.app.commun.toucherDescription
import fr.coprofirst.app.commun.toucherTag
import fr.coprofirst.app.commun.toucherTexte
import fr.coprofirst.app.entree.NavigationEntree
import fr.coprofirst.app.entree.ServeurDeTest
import fr.coprofirst.app.entree.entrerDansLaResidence
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Plus › À propos (#27), comme l'iOS : la mention servie, les deux textes légaux de la
 * résidence lus dans l'app (« Logiciel libre » sous les mentions), le code de l'app offert
 * (AGPL §13), chaque composant tiers et sa licence entière.
 */
@RunWith(AndroidJUnit4::class)
class AProposInterfaceTest {
    @get:Rule
    val ecran = createComposeRule()
    private val serveur = ServeurDeTest()

    private val residence = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val chemin = request.url.encodedPath
            return when {
                chemin.endsWith("/config") ->
                    json(200, """{"site_nom": "Les Glycines", "pied_de_page_annee_debut": "2024", "pied_de_page_texte": "Syndicat des Glycines"}""")
                chemin.endsWith("/config/legal") ->
                    json(200, """{"mentions_legales": "<h2>Éditeur</h2><p>Le syndicat des copropriétaires.</p>", "politique_confidentialite": ""}""")
                chemin.endsWith("/auth/login") -> json(200, "{}")
                else -> json(404, """{"detail": "Introuvable."}""")
            }
        }
    }

    private fun json(code: Int, corps: String) =
        MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(corps).build()

    @Before
    fun ouvrirAPropos() {
        serveur.serveur.dispatcher = residence
        ecran.setContent { NavigationEntree(serveur.apparence(), serveur.parcours()) }
        ecran.entrerDansLaResidence("residence.exemple.test", "Les Glycines")
        ecran.attendreTexte("Plus")
        ecran.toucherTexte("Plus")
        ecran.toucherTag("plus.a-propos")
        ecran.attendreTexte("Syndicat des Glycines")
    }

    @After
    fun fermer() = serveur.close()

    @Test
    fun laMentionDitLAnneeLeTexteEtLaVersion() {
        val mention = ecran.onNodeWithTag(ReperesAPropos.MENTION)
        listOf("Les Glycines", "© 2024–", " ·\u00A0Syndicat des Glycines ·\u00A0app ").forEach { mention.assert(hasText(it, substring = true)) }
    }

    @Test
    fun lesMentionsSeLisentDansLAppEtFinissentParLeLogicielLibre() {
        ecran.toucherTag(ReperesAPropos.MENTIONS)
        ecran.attendreTexte("Le syndicat des copropriétaires.")
        ecran.amenerTag(ReperesPageLegale.LOGICIEL_LIBRE)
        ecran.toucherDescription("Retour")

        // Un texte que la résidence n'a pas publié se dit.
        ecran.toucherTag(ReperesAPropos.CONFIDENTIALITE)
        ecran.attendreTexte("La résidence n'a pas encore publié ce texte.")
    }

    @Test
    fun leCodeDeLAppEtLesComposantsTiersSontOfferts() {
        ecran.amenerTag(ReperesAPropos.CODE_APP)
        ecran.toucherTag(ReperesAPropos.composant("OkHttp"))
        ecran.attendreTexte("Public Suffix List")
        ecran.attendreTexte("Mozilla Public License Version 2.0")
        assertTrue(ecran.onAllNodes(hasText("Apache License", substring = true)).fetchSemanticsNodes().isNotEmpty())
    }
}
