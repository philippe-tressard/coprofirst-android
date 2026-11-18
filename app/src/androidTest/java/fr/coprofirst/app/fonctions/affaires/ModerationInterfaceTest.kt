// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.affaires

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.coprofirst.app.ThemeCoproFirst
import fr.coprofirst.app.commun.AvisDuGeste
import fr.coprofirst.app.commun.AvisEphemere
import fr.coprofirst.app.commun.attendreTag
import fr.coprofirst.app.commun.attendreTexte
import fr.coprofirst.app.commun.masquages.ReperesMasquage
import fr.coprofirst.app.commun.toucher
import fr.coprofirst.app.session.Installation
import fr.coprofirst.app.session.SessionOuverte
import fr.coprofirst.app.session.SessionsParHote
import fr.coprofirst.app.session.VersionContrat
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** #282 : masquer ce que publie l'auteur d'une suite, comme l'iOS (`ModerationUITests`), sur une installation qui le sert. */
@RunWith(AndroidJUnit4::class)
class ModerationInterfaceTest {
    @get:Rule
    val ecran = createComposeRule()

    private val session = SessionOuverte(
        Installation("https://residence.exemple.test", "Les Glycines", versionContrat = VersionContrat.MODERATION.toString()),
        SessionsParHote(),
    )

    private fun el(tag: String): SemanticsNodeInteraction = ecran.onNodeWithTag(tag, useUnmergedTree = true)

    @Test
    fun lAuteurDUneSuiteSeMasque() {
        val service = AffairesSimulees()
        val avis = AvisDuGeste()
        val fiche = ContexteFiche(service, session, false, avis, null, moi = 7) { "https://residence.exemple.test/tickets/$it" }
        ecran.setContent { ThemeCoproFirst { AvisEphemere(avis) { FicheAffaire(1, fiche) } } }

        ecran.attendreTag(ReperesAffaires.gestesSuite(31), useUnmergedTree = true)
        el(ReperesAffaires.gestesSuite(31)).toucher()
        ecran.attendreTag(ReperesAffaires.gesteSuite(GesteSuite.Masquer), useUnmergedTree = true)
        el(ReperesAffaires.gesteSuite(GesteSuite.Masquer)).toucher()
        ecran.attendreTexte("Masquer ce que publie Le syndic ?", useUnmergedTree = true)
        el(ReperesMasquage.CONFIRMER).toucher()
        ecran.attendreTexte("Ce que publie Le syndic est masqué pour vous", useUnmergedTree = true)
        assertEquals(listOf(31), service.masquesPoses.map { it.id })
    }
}
