// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.profil

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.coprofirst.app.ThemeCoproFirst
import fr.coprofirst.app.commun.attendreDisparitionTag
import fr.coprofirst.app.commun.attendreTag
import fr.coprofirst.app.commun.attendreTexte
import fr.coprofirst.app.commun.masquages.AuteurAMasquer
import fr.coprofirst.app.commun.masquages.Masquage
import fr.coprofirst.app.commun.masquages.ReperesMasquage
import fr.coprofirst.app.commun.masquages.ServiceMasquages
import fr.coprofirst.app.commun.toucherTag
import fr.coprofirst.app.session.ErreurService
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * Profil › « Personnes masquées » (#282), comme l'iOS : « Ne plus masquer » retire la
 * personne de la liste et le dit par l'avis ; un refus du serveur se dit par l'avis, avec
 * sa phrase, et la personne reste (#324 : le comportement que la factorisation garde).
 */
@RunWith(AndroidJUnit4::class)
class PersonnesMasqueesInterfaceTest {
    @get:Rule
    val ecran = createComposeRule()

    @Volatile
    private var refuser = true
    private val demasques = mutableListOf<Int>()

    private val service = object : ServiceMasquages {
        override suspend fun masques() = listOf(Masquage(31, "Dominique EXEMPLE", Instant.parse("2026-11-02T10:00:00Z")))
        override suspend fun masquer(auteur: AuteurAMasquer) = Unit
        override suspend fun demasquer(auteurId: Int) {
            if (refuser) throw ErreurService.Refus("Ce masquage est introuvable.", 404)
            synchronized(demasques) { demasques += auteurId }
        }
    }

    @Test
    fun unePersonneSeDemasqueOuLeRefusSeDit() {
        ecran.setContent { ThemeCoproFirst { FeuillePersonnesMasquees(service) {} } }
        ecran.toucherTag(ReperesMasquage.demasquer(31))
        ecran.attendreTexte("Ce masquage est introuvable.")
        ecran.attendreTag(ReperesMasquage.masque(31))

        refuser = false
        ecran.toucherTag(ReperesMasquage.demasquer(31))
        ecran.attendreTexte("Vous voyez de nouveau ce que publie Dominique EXEMPLE")
        ecran.attendreDisparitionTag(ReperesMasquage.masque(31))
        assertEquals(listOf(31), synchronized(demasques) { demasques.toList() })
    }
}
