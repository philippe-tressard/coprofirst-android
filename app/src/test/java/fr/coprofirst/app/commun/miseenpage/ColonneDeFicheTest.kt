// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.miseenpage

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** #69, comme `ListeEtFicheTests` de l'iOS : la largeur d'une liste, et la fiche de la colonne. */
class ColonneDeFicheTest {
    @Test
    fun laListeALaLargeurIdealeJamaisPlusDeLaMoitie() {
        assertEquals(360.dp, ColonneDeFiche.largeurDeListe(1100.dp))
        assertEquals(300.dp, ColonneDeFiche.largeurDeListe(600.dp))
        assertEquals(250.dp, ColonneDeFiche.largeurDeListe(500.dp))
    }

    @Test
    fun laFicheOuverteSeReconnaitParSaNatureEtSonIdentifiant() {
        val colonne = ColonneDeFiche()
        assertFalse(colonne.occupee)
        colonne.ouverte = "sondage" to "3"
        assertTrue(colonne.estOuverte("sondage", "3"))
        assertFalse(colonne.estOuverte("affaire", "3"))
    }
}
