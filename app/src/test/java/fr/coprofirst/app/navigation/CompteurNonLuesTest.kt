// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.navigation

import fr.coprofirst.app.session.ErreurService
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Le nombre de non lues de la cloche, par résidence, comme `CompteurNonLues` de l'iOS. */
class CompteurNonLuesTest {
    private val glycines = "https://glycines.test"
    private val tilleuls = "https://tilleuls.test"

    @Test
    fun aucuneLectureAucunPoint() = assertEquals(0, CompteurNonLues().nombre(glycines))

    @Test
    fun chaqueResidenceASonNombre() {
        val compteur = CompteurNonLues()
        compteur.noter(glycines, 12)
        compteur.noter(tilleuls, 3)
        assertEquals(12, compteur.nombre(glycines))
        assertEquals(3, compteur.nombre(tilleuls))

        compteur.noter(glycines, 0)
        assertEquals("tout lu aux Glycines : leur point s'éteint", 0, compteur.nombre(glycines))
        assertEquals("celui des Tilleuls reste", 3, compteur.nombre(tilleuls))
    }

    @Test
    fun unNombreNegatifNePeutPasEtre() {
        val compteur = CompteurNonLues()
        compteur.noter(glycines, -1)
        assertEquals(0, compteur.nombre(glycines))
    }

    @Test
    fun laRelectureNoteLeNombreServi() = runTest {
        val compteur = CompteurNonLues()
        compteur.noter(glycines, 2)
        compteur.relire(glycines) { 5 }
        assertEquals(5, compteur.nombre(glycines))
        assertEquals("l'autre résidence n'est pas lue", 0, compteur.nombre(tilleuls))
    }

    @Test
    fun uneRelectureEchoueeGardeLeNombre() = runTest {
        val compteur = CompteurNonLues()
        compteur.noter(glycines, 4)
        compteur.relire(glycines) { throw ErreurService.Injoignable }
        assertEquals("sans réseau, la cloche ne s'éteint pas", 4, compteur.nombre(glycines))
    }
}
