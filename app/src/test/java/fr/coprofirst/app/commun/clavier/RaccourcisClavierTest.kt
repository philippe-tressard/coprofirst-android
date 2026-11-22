// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.clavier

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** #69, coprofirst-ios#223 : Ctrl+N, Ctrl+R et Ctrl+F au clavier d'une tablette, offerts par l'écran affiché. */
class RaccourcisClavierTest {
    @Test
    fun ctrlDeclencheCeQueLEcranOffre() {
        val raccourcis = RaccourcisClavier()
        var nouvelles = 0
        raccourcis.inscrire(Raccourci.NouvelleAffaire) { nouvelles++ }
        assertTrue(raccourcis.declencher(KeyEvent.KEYCODE_N, ctrl = true))
        assertEquals(1, nouvelles)
        // Sans Ctrl, avec Maj en plus, ou une touche que rien n'offre : rien.
        assertFalse(raccourcis.declencher(KeyEvent.KEYCODE_N, ctrl = false))
        assertFalse(raccourcis.declencher(KeyEvent.KEYCODE_N, ctrl = true, autresModificateurs = true))
        assertFalse(raccourcis.declencher(KeyEvent.KEYCODE_R, ctrl = true))
        assertEquals(1, nouvelles)
    }

    /** La liste et sa fiche côte à côte s'actualisent toutes deux ; l'écran parti, plus rien. */
    @Test
    fun ctrlRActualiseToutCeQuiEstALEcranPuisRienApresLeDepart() {
        val raccourcis = RaccourcisClavier()
        var liste = 0
        var fiche = 0
        val retirerListe = raccourcis.inscrire(Raccourci.Actualiser) { liste++ }
        val retirerFiche = raccourcis.inscrire(Raccourci.Actualiser) { fiche++ }
        assertEquals(listOf(Raccourci.Actualiser), raccourcis.offerts())
        assertTrue(raccourcis.declencher(KeyEvent.KEYCODE_R, ctrl = true))
        assertEquals(1 to 1, liste to fiche)
        retirerFiche()
        retirerListe()
        assertFalse(raccourcis.declencher(KeyEvent.KEYCODE_R, ctrl = true))
        assertEquals(emptyList<Raccourci>(), raccourcis.offerts())
    }

    /** Ctrl+F (coprofirst-ios#223) : la dernière recherche inscrite seule, puis celle d'avant à son départ. */
    @Test
    fun ctrlFNOuvreQueLaDerniereRecherche() {
        val raccourcis = RaccourcisClavier()
        val ouvertes = mutableListOf<String>()
        raccourcis.inscrire(Raccourci.Rechercher) { ouvertes += "liste" }
        val retirerFiche = raccourcis.inscrire(Raccourci.Rechercher) { ouvertes += "fiche" }
        assertTrue(raccourcis.declencher(KeyEvent.KEYCODE_F, ctrl = true))
        assertEquals(listOf("fiche"), ouvertes)
        retirerFiche()
        assertTrue(raccourcis.declencher(KeyEvent.KEYCODE_F, ctrl = true))
        assertEquals(listOf("fiche", "liste"), ouvertes)
    }

    @Test
    fun laListeDuSystemeSuitLOrdreDesRaccourcis() {
        val raccourcis = RaccourcisClavier()
        raccourcis.inscrire(Raccourci.Actualiser) {}
        raccourcis.inscrire(Raccourci.NouvelleAffaire) {}
        assertEquals(listOf(Raccourci.NouvelleAffaire, Raccourci.Actualiser), raccourcis.offerts())
    }
}
