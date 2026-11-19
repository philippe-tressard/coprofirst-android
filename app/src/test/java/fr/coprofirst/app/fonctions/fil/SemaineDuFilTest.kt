// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.fil

import fr.coprofirst.app.commun.fil.CibleDuFil
import fr.coprofirst.app.commun.fil.ElementDuFil
import fr.coprofirst.app.commun.fil.JourDuFil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** #286, comme `SemaineDuFilTests` et `ZoomDuCarnetTests` de l'iOS (coprofirst-ios#277). */
class SemaineDuFilTest {
    private fun element(id: String, publie: String, jour: String?) =
        ElementDuFil(id, "ticket_ouvert", Instant.parse("${publie}T10:00:00Z"), "Titre $id", null, cible = CibleDuFil.Resume(null), jour = jour)

    private val lundi = LocalDate.parse("2026-11-16")

    @Test
    fun leLundiDeLaSemaine() {
        assertEquals(lundi, SemaineDuFil.lundi(LocalDate.parse("2026-11-19")))
        assertEquals(lundi, SemaineDuFil.lundi(lundi))
        assertEquals(lundi, SemaineDuFil.lundi(LocalDate.parse("2026-11-22")))
    }

    /** Chaque carte au jour SERVI — une échéance du jeudi publiée le lundi va au jeudi ; rien n'est recalculé. */
    @Test
    fun lesCartesSeRangentAuJourServi() {
        val echeance = element("a", publie = "2026-11-16", jour = "2026-11-19")
        val publiee = element("b", publie = "2026-11-17", jour = "2026-11-17")
        val horsSemaine = element("c", publie = "2026-11-10", jour = "2026-11-10")
        val semaine = SemaineDuFil.ranger(lundi, listOf(echeance, publiee, horsSemaine))
        assertEquals(7, semaine.size)
        assertEquals((0L..6L).map { lundi.plusDays(it) }, semaine.map { it.jour })
        assertEquals(listOf("a"), semaine[3].elements.map { it.id })
        assertEquals(listOf("b"), semaine[1].elements.map { it.id })
        assertTrue("un jour vide : « Rien ce jour »", semaine[0].elements.isEmpty())
        // Du lundi au jeudi à gauche du pli, du vendredi au dimanche à droite.
        assertEquals(LocalDate.parse("2026-11-19"), semaine.take(SemaineDuFil.A_GAUCHE).last().jour)
    }

    @Test
    fun pasDeSemaineAvantCelleDuPlusAncienJourDuFil() {
        val jours = listOf(JourDuFil("2026-11-18", emptyList()), JourDuFil("2026-11-10", emptyList()))
        assertEquals(LocalDate.parse("2026-11-09"), SemaineDuFil.precedente(lundi, jours))
        assertNull(SemaineDuFil.precedente(LocalDate.parse("2026-11-09"), jours))
        assertEquals(LocalDate.parse("2026-11-23"), SemaineDuFil.suivante(lundi))
    }

    /** Un jour sans carte publiée rouvre le jour du fil le plus proche ; une carte, le jour où le fil la publie. */
    @Test
    fun leJourARouvrir() {
        val carte = element("a", publie = "2026-11-16", jour = "2026-11-19")
        val jours = listOf(JourDuFil("2026-11-18", emptyList()), JourDuFil("2026-11-16", listOf(carte)), JourDuFil("2026-11-10", emptyList()))
        assertEquals("2026-11-18", SemaineDuFil.jourDuFil(jours, LocalDate.parse("2026-11-18")))
        assertEquals("2026-11-18", SemaineDuFil.jourDuFil(jours, LocalDate.parse("2026-11-20")))
        assertEquals("2026-11-18", SemaineDuFil.jourDuFil(jours, LocalDate.parse("2026-11-17")))
        assertEquals("2026-11-16", SemaineDuFil.jourDeLaCarte(jours, carte, LocalDate.parse("2026-11-19")))
        assertNull(SemaineDuFil.jourDuFil(emptyList(), lundi))
    }

    @Test
    fun leZoomPasseAuSeuilOuSurUnGesteVif() {
        val pincer = ZoomDuCarnet.Sens.Pincer
        assertTrue(ZoomDuCarnet.passe(pincer, 0.84f, 0f))
        assertFalse(ZoomDuCarnet.passe(pincer, 0.9f, 0f))
        assertTrue("vif dans le bon sens", ZoomDuCarnet.passe(pincer, 0.95f, -2f))
        assertFalse("vif à contresens", ZoomDuCarnet.passe(pincer, 0.95f, 2f))
        assertFalse("écarter n'ouvre pas la semaine", ZoomDuCarnet.passe(pincer, 1.3f, 0f))
        assertTrue(ZoomDuCarnet.passe(ZoomDuCarnet.Sens.Ecarter, 1.16f, 0f))
        assertFalse(ZoomDuCarnet.passe(ZoomDuCarnet.Sens.Ecarter, 0.7f, 0f))
    }

    /** 1:1 jusqu'au seuil, puis élastique : la page résiste, sans jamais repartir en arrière. */
    @Test
    fun laPageSuitLesDoigtsPuisResiste() {
        assertEquals(0.9f, ZoomDuCarnet.montree(0.9f), 1e-6f)
        assertEquals(1.1f, ZoomDuCarnet.montree(1.1f), 1e-6f)
        val loin = ZoomDuCarnet.montree(0.3f)
        assertTrue("élastique : moins loin que les doigts", loin > 0.3f && loin < 1f - ZoomDuCarnet.SEUIL)
        assertTrue("monotone", ZoomDuCarnet.montree(0.2f) < loin)
        assertTrue(ZoomDuCarnet.montree(3f) < 1f + ZoomDuCarnet.SEUIL + 0.25f)
    }
}
