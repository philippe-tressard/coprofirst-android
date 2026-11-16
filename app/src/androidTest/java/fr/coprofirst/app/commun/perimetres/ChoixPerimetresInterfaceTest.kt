// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.perimetres

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.coprofirst.app.ThemeCoproFirst
import fr.coprofirst.app.commun.toucherTag
import java.util.Collections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Le choix des périmètres et le lieu que le serveur nomme (#129) — sans l'écran de l'Espace
 * CS : si le lieu ne paraît pas ici, le défaut est dans la couche commune ; s'il paraît, la
 * cause est dans l'écran ou le test qui l'emploie.
 */
@RunWith(AndroidJUnit4::class)
class ChoixPerimetresInterfaceTest {
    @get:Rule
    val ecran = createComposeRule()

    private val arbre = ArbreDesPerimetres(
        listOf(
            Perimetre("copropriete", "Toute la copropriété", parDefaut = true),
            Perimetre("batiments", "Bâtiments", selectionnable = false),
            Perimetre("bat_a", "Bâtiment A", profondeur = 1, parent = "batiments", niveauxLibres = 1),
            Perimetre("bat_a/hall", "Hall d'entrée", profondeur = 2, parent = "bat_a", libelleCourt = "Hall", niveauxLibres = 0),
            Perimetre("parking", "Parking"),
        ),
    )

    /** Le service comme le réseau le rend : la réponse arrive d'un autre fil, un instant après. */
    private class ServiceLent : ServicePerimetres {
        val demandes: MutableList<List<String>> = Collections.synchronizedList(mutableListOf())
        override suspend fun perimetres() = emptyList<Perimetre>()
        override suspend fun lieu(codes: List<String>): String = withContext(Dispatchers.IO) {
            demandes += codes
            delay(200)
            if (codes == listOf("copropriete")) "Toute la copropriété" else "Bâtiment A › Toit"
        }
    }

    @Test
    fun leLieuDuDefautPuisCeluiDeLaTeteSontNommesParLeServeur() {
        val service = ServiceLent()
        ecran.setContent {
            ThemeCoproFirst {
                var choix by remember { mutableStateOf(ChoixDePerimetres().choisirDefaut(arbre)) }
                ChoixPerimetres(arbre, choix, { choix = it }, repere = "essai.perimetre", service = service)
            }
        }
        ecran.toucherTag("essai.perimetre.bat_a")
        // Le lieu se LIT d'un bloc, « Lieu : … » (EtiquetteLieu remplace la sémantique de son
        // texte) : il se cherche par sa description, pas par son texte — c'était la cause de #129.
        ecran.waitUntil(10_000) {
            ecran.onAllNodes(hasTestTag("essai.perimetre.lieu") and hasContentDescription("Bâtiment A › Toit", substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(listOf("bat_a"), service.demandes.last())
    }
}
