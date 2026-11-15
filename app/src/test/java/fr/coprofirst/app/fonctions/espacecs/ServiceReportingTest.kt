// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.espacecs

import fr.coprofirst.app.charte.Palettes
import fr.coprofirst.app.navigation.Destination
import fr.coprofirst.app.navigation.RegistreDesMenus
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Le Reporting, par le client GÉNÉRÉ, contre la résidence simulée : l'écran ne recalcule rien. */
class ServiceReportingTest {
    private val banc = ServeurCS()
    private val service = ServiceReportingApi(banc.session)

    @After
    fun arreter() = banc.close()

    @Test
    fun leSuiviSeLitTelQueServi() = runTest {
        val suivi = service.suivi(ReleveSuivi.Affaires)
        assertEquals(3, suivi.total)
        assertEquals(2, suivi.anciennes)
        assertEquals(1, suivi.fusionnees)
        assertEquals(listOf(12, 18, 31), suivi.affaires.map { it.id })
        val fuite = suivi.affaires[0]
        assertEquals("badge-red", fuite.teinteAge)
        assertEquals("badge-orange", fuite.teinteStatut)
        assertEquals("Chez le syndic", fuite.libelleStatut)
        assertTrue(fuite.apercuIA)
        assertEquals("Nouveau message depuis le dernier résumé", fuite.evolution)
        assertEquals("Du nouveau depuis le dernier résumé", fuite.copy(motifEvolution = "inconnu").evolution)
        assertEquals("2026-04-10", fuite.ouverteLe)
        assertEquals(listOf("p1", "p2"), suivi.parCategorie[0].tranches.map { it.teinte })
        assertTrue(banc.residence.recues.any { it.startsWith("GET /api/suivi-affaires?mode=affaires") })
    }

    @Test
    fun lEntretienDitLEtatDeSaVisite() = runTest {
        val suivi = service.suivi(ReleveSuivi.duMode("entretien"))
        assertEquals("À venir", suivi.affaires.single().visite?.libelle)
        assertEquals(ReleveSuivi.Affaires, ReleveSuivi.duMode("relance"))
        assertNull(suivi.affaires.single().copy(etatVisite = null).visite)
    }

    @Test
    fun lesRenouvellementsSeRangentCommeLeServeurLesClasse() = runTest {
        val releve = service.renouvellements()
        assertEquals(2026, releve.annee)
        assertEquals(listOf(7), releve.contratsDeLAnnee.map { it.id })
        assertEquals(listOf(8), releve.contratsFuturs.map { it.id })
        assertEquals(listOf(9), releve.contratsSansDate.map { it.id })
        assertEquals(2.5, releve.contrats[0].noteMoyenne)
        assertEquals("2026-08-31", releve.contrats[0].datePreavis)
        assertEquals(listOf("2026", "2031"), releve.diagnosticsParAnnee.map { it.first })
        assertEquals(listOf(3), releve.diagnosticsPermanents.map { it.id })
        assertEquals("depasse", releve.diagnostics[0].urgence)
    }

    /** Une tranche se peint par les jetons de la charte : deux jetons, des hachures ; inconnue, `textMuted`. */
    @Test
    fun lesTranchesSePeignentParLaCharte() {
        val c = Palettes.pour(null).clair
        assertEquals(listOf(c.serie1), couleursDeTranche("s1", c))
        assertEquals(listOf(c.palier2, c.neutreFond), couleursDeTranche("plusieurs", c))
        assertEquals(listOf(c.textMuted), couleursDeTranche("inconnue", c))
        assertEquals(listOf(c.textMuted), couleursDeTranche(null, c))
    }

    /**
     * Une affaire s'ouvre par la porte, sous sa nature « affaire » — et seulement quand un menu
     * déclare sa fiche (Mes affaires) : sinon, pas de lien qui ne mène nulle part.
     */
    @Test
    fun uneAffaireSOuvreParLaPorte() {
        val demandees = mutableListOf<Destination>()
        val lien = lienAffaire({ demandees += it; true }, 12)
        assertEquals("affaire" in RegistreDesMenus.fiches, lien != null)
        lien?.invoke()
        if (lien != null) assertEquals(listOf(Destination.Element("affaire", "12")), demandees)
    }
}
