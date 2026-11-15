// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.admin

import fr.coprofirst.api.modeles.IndicateursTelemetrie
import fr.coprofirst.api.modeles.Record
import fr.coprofirst.app.R
import fr.coprofirst.app.commun.LibelleTeinte
import fr.coprofirst.app.commun.formats.FormatsDuSite
import fr.coprofirst.app.session.ErreurService
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

/** Les règles du Paramétrage, celles de l'iOS (`AdminTests`) : libellés, nombres clés, graphe, état de la télémétrie. */
class ReglesAdminTest {
    @Test
    fun lesTachesSeNommentParLesTablesDuSite() {
        assertEquals("Sauvegarde quotidienne", LibellesMaintenance.tache("backup"))
        assertEquals("une tâche inconnue garde son code", "nouvelle_tache", LibellesMaintenance.tache("nouvelle_tache"))
        assertEquals(LibelleTeinte("Exécution manquante", "badge-red"), LibellesMaintenance.statut("manquante"))
        assertEquals("badge-gray", LibellesMaintenance.statut("inconnu").badge)
        assertNull("« à jour » n'a pas d'aide", LibellesMaintenance.aide("ok"))
        assertTrue(LibellesMaintenance.aide("erreur").orEmpty().isNotBlank())
        assertEquals("NŒUD-B", LibellesMaintenance.noeud("nœud-b"))
        val anomalie = SanteTaches.Anomalie("telemetrie", "nœud-a", null, "x")
        assertEquals("Agrégation télémétrie · nœud-a", LibellesMaintenance.anomalie(anomalie))
    }

    @Test
    fun lesNombresClesSuiventLOrdreDuSite() {
        val kpi = IndicateursTelemetrie(
            vues = 1201,
            pages = 18,
            utilisateurs = 16,
            heurePointe = "18h",
            moyVuesJour = BigDecimal("40.0"),
            recordJour = Record(uniques = 16, jour = "2026-11-04"),
        )
        val jour = LecturesTelemetrie.indicateurs(kpi, PorteeTelemetrie.Jour)
        assertEquals(
            listOf(
                R.string.admin_telemetrie_kpi_vues_jour,
                R.string.admin_telemetrie_kpi_utilisateurs_jour,
                R.string.admin_telemetrie_kpi_pages,
                R.string.admin_telemetrie_kpi_heure_pointe,
                R.string.admin_telemetrie_kpi_moy_vues_jour,
                R.string.admin_telemetrie_kpi_record_jour,
            ),
            jour.map { it.libelle },
        )
        assertEquals(FormatsDuSite.nombre(1201), jour.first().valeur)
        assertEquals(Indicateur("16", R.string.admin_telemetrie_kpi_record_jour, FormatsDuSite.date("2026-11-04"), enUtilisateurs = true), jour.last())
        val mois = LecturesTelemetrie.indicateurs(kpi, PorteeTelemetrie.Mois)
        assertEquals(R.string.admin_telemetrie_kpi_vues_mois, mois[0].libelle)
        assertEquals(R.string.admin_telemetrie_kpi_utilisateurs_pic, mois[1].libelle)
    }

    @Test
    fun lesEtiquettesDuGrapheSelonLaVue() {
        assertEquals("18h", LecturesTelemetrie.etiquette("18h", PorteeTelemetrie.Jour))
        assertEquals("4", LecturesTelemetrie.etiquette("11-04", PorteeTelemetrie.Mois))
        assertEquals("4 nov.", LecturesTelemetrie.libelle("11-04", PorteeTelemetrie.Mois))
        assertEquals("nov.", LecturesTelemetrie.etiquette("2026-11", PorteeTelemetrie.Annee))
        assertEquals("novembre 2026", LecturesTelemetrie.libelle("2026-11", PorteeTelemetrie.Annee))
        assertEquals("2026", LecturesTelemetrie.etiquette("2026", PorteeTelemetrie.Total))
    }

    @Test
    fun lesGraduationsSontRondesEtCouvrentLeMaximum() {
        assertEquals(listOf(0, 1), Graduations.de(0))
        assertEquals(listOf(0, 1), Graduations.de(1))
        assertEquals(listOf(0, 10, 20, 30), Graduations.de(22))
        assertEquals(listOf(0, 50, 100, 150), Graduations.de(146))
        assertEquals(listOf(0, 500, 1000, 1500), Graduations.de(1201))
        for (maximum in listOf(3, 7, 99, 101, 999, 12345)) {
            val g = Graduations.de(maximum)
            assertTrue("$maximum → $g", g.last() >= maximum && g.size in 2..6)
        }
    }

    @Test
    fun laTelemetrieGardeLeFiltreAppliqueEtOublieUnTableauEnEchec() = runTest {
        val service = ServiceSimule()
        val etat = EtatTelemetrie(service)
        etat.sansDemande = true
        etat.charger()
        assertEquals(PorteeTelemetrie.Jour, service.demandes.single().first)
        assertFalse("le serveur a appliqué « avec » : le filtre se lit tel qu'appliqué", etat.sansAffiche)
        assertEquals(PorteeTelemetrie.Jour, etat.porteeLue)

        service.echec = ErreurService.Injoignable
        etat.portee = PorteeTelemetrie.Mois
        etat.charger()
        assertNull("un tableau d'une autre vue ne passe pas pour celle-ci", etat.tableau)
        assertEquals(ErreurService.Injoignable, etat.erreur)

        service.echec = null
        etat.charger()
        assertNull(etat.erreur)
        assertEquals(PorteeTelemetrie.Mois, etat.porteeLue)
    }

    private class ServiceSimule : ServiceAdmin {
        val demandes = mutableListOf<Pair<PorteeTelemetrie, Boolean>>()
        var echec: ErreurService? = null

        override suspend fun santeTaches() = SanteTaches(emptyList(), emptyList(), 7)

        override suspend fun dernierExport() = DernierExport(null, Instant.EPOCH, 0, 0, 0)

        override suspend fun tableauTelemetrie(portee: PorteeTelemetrie, sansGestionnaire: Boolean): TableauTelemetrie {
            demandes += portee to sansGestionnaire
            echec?.let { throw it }
            return TableauTelemetrie(
                IndicateursTelemetrie(vues = 3, pages = 1),
                emptyList(),
                "Vues",
                emptyList(),
                emptyList(),
                TableauTelemetrie.FiltreGestionnaire(propose = false, gestionnaireDesigne = true, sansApplique = false, nonDistingueJusquAu = null),
            )
        }
    }
}
