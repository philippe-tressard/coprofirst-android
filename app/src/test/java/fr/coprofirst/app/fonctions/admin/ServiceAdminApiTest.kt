// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.admin

import fr.coprofirst.app.session.AdresseInstallation
import fr.coprofirst.app.session.ErreurService
import fr.coprofirst.app.session.Installation
import fr.coprofirst.app.session.SessionOuverte
import fr.coprofirst.app.session.SessionsParHote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant

/** Chaque lecture du Paramétrage part par SA route du contrat, par le client généré, et se lit comme l'iOS la lit. */
class ServiceAdminApiTest {
    private val serveur = MockWebServer().apply { start() }
    private val base = { _: AdresseInstallation -> serveur.url("/api").toString() }
    private val session = SessionOuverte(
        Installation("https://residence.exemple.test", "Les Glycines"),
        SessionsParHote(OkHttpClient()),
        Dispatchers.Unconfined,
        base,
    )
    private val service = ServiceAdminApi(session)

    @After
    fun arreter() = serveur.close()

    private fun repondre(code: Int, corps: String) = serveur.enqueue(
        MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(corps).build(),
    )

    @Test
    fun laSanteDesTachesSeLitDuContrat() = runTest {
        repondre(200, FixturesAdmin.SANTE)
        val sante = service.santeTaches()
        assertEquals("GET /api/admin/maintenance/sante", serveur.takeRequest().let { "${it.method} ${it.url.encodedPath}" })
        assertEquals(3, sante.taches.size)
        assertEquals(
            SanteTaches.Tache("bascule", "ok", "nœud-a", Instant.parse("2026-11-10T02:30:00Z"), noeudEnRetard = "nœud-b"),
            sante.taches[1],
        )
        assertNull("une tâche sans nœud ni date", sante.taches[2].derniere)
        assertEquals(7, sante.anomaliesFenetreJours)
        assertEquals("Agrégation interrompue.", sante.anomaliesRecentes.single().erreur)
    }

    @Test
    fun leDernierExportSeLitDuContrat() = runTest {
        repondre(200, """{"archive": "export-2026-11-09.tar.gz", "cree_le": "2026-11-09T02:40:00Z", "octets": 48234496, "lignes": 152340, "fichiers": 318}""")
        val export = service.dernierExport()
        assertEquals("GET /api/admin/export-copropriete/dernier", serveur.takeRequest().let { "${it.method} ${it.url.encodedPath}" })
        assertEquals(DernierExport("export-2026-11-09.tar.gz", Instant.parse("2026-11-09T02:40:00Z"), 48234496, 152340, 318), export)
        repondre(200, "{}")
        assertEquals("aucun export : rien n'est servi", DernierExport(null, null, 0, 0, 0), service.dernierExport())
    }

    @Test
    fun leTableauDemandeSaVueEtSonFiltre() = runTest {
        repondre(200, FixturesAdmin.tableau())
        val tableau = service.tableauTelemetrie(PorteeTelemetrie.Annee, sansGestionnaire = true)
        val requete = serveur.takeRequest()
        assertEquals("/api/telemetry/dashboard", requete.url.encodedPath)
        assertEquals("annee", requete.url.queryParameter("scope"))
        assertEquals("sans", requete.url.queryParameter("gestionnaire"))
        assertEquals("Vues par heure", tableau.titreGraphe)
        assertEquals(listOf(TableauTelemetrie.Baton("8h", 6, 3), TableauTelemetrie.Baton("9h", 14, null)), tableau.graphe)
        assertEquals(TableauTelemetrie.PageVue("/tableau-de-bord", 58, 21), tableau.pages.single())
        assertEquals("Camille EXEMPLE", tableau.utilisateurs.single().nom)
        assertEquals(TableauTelemetrie.FiltreGestionnaire(true, true, sansApplique = false, nonDistingueJusquAu = null), tableau.filtre)

        repondre(200, FixturesAdmin.tableau())
        service.tableauTelemetrie(PorteeTelemetrie.Jour, sansGestionnaire = false)
        assertEquals("avec", serveur.takeRequest().url.queryParameter("gestionnaire"))
    }

    /** #127 : la répartition par accès (contrat ≥ 1.31.0), servie et lue telle quelle — rien ne se recalcule. */
    @Test
    fun laRepartitionParAccesSeLitTelleQuelle() = runTest {
        repondre(200, FixturesAdmin.tableau(canaux = true))
        val tableau = service.tableauTelemetrie(PorteeTelemetrie.Jour, sansGestionnaire = false)
        assertEquals(
            listOf(
                TableauTelemetrie.PartCanal("web", "Site web", 110, 75, 18),
                TableauTelemetrie.PartCanal("android", "App Android", 36, 25, null),
            ),
            tableau.canaux,
        )
        val page = tableau.pages.single()
        assertEquals(listOf(TableauTelemetrie.VuesCanal("web", "Site web", 40), TableauTelemetrie.VuesCanal("android", "App Android", 18)), page.canaux)
        assertTrue(tableau.plusieursCanauxParPage)
        assertEquals("Site web\u00A040 · App Android\u00A018", LecturesTelemetrie.accesDe(page))
    }

    /** Un serveur sans la répartition (contrat < 1.31.0) : aucun bloc, aucune colonne. */
    @Test
    fun sansRepartitionRienNeSAjoute() = runTest {
        repondre(200, FixturesAdmin.tableau())
        val tableau = service.tableauTelemetrie(PorteeTelemetrie.Jour, sansGestionnaire = false)
        assertEquals(emptyList<TableauTelemetrie.PartCanal>(), tableau.canaux)
        assertFalse(tableau.plusieursCanauxParPage)
    }

    @Test
    fun unRefusSortAvecSaPhrase() = runTest {
        repondre(403, """{"detail": "Réservé à l'opérateur de la plateforme."}""")
        try {
            service.santeTaches()
            fail("un refus est une erreur")
        } catch (e: ErreurService) {
            assertTrue(e.toString(), e is ErreurService.Refus)
            assertEquals("Réservé à l'opérateur de la plateforme.", (e as ErreurService.Refus).phraseServie)
        }
    }
}

/** Les réponses inventées du Paramétrage, à la forme des routes : aucun nom réel, aucune machine réelle. */
object FixturesAdmin {
    /** La répartition par accès servie (contrat ≥ 1.31.0) : `canaux` du tableau, puis d'une page. */
    private const val CANAUX = """
      "canaux": [{"canal": "web", "libelle": "Site web", "vues": 110, "part": 75, "visiteurs": 18},
                 {"canal": "android", "libelle": "App Android", "vues": 36, "part": 25}],"""
    private const val PAGE_CANAUX = """, "canaux": [{"canal": "web", "libelle": "Site web", "vues": 40}, {"canal": "android", "libelle": "App Android", "vues": 18}]"""

    const val SANTE = """{
      "taches": [
        {"tache": "maintenance", "noeud": "nœud-a", "noeuds": [], "noeud_enregistre": true, "statut": "vigilance",
         "derniere": "2026-11-09T03:12:00Z", "periodicite_heures": 192},
        {"tache": "bascule", "noeud": "nœud-a", "noeuds": [], "noeud_enregistre": true, "statut": "ok",
         "derniere": "2026-11-10T02:30:00Z", "periodicite_heures": 30, "noeud_en_retard": "nœud-b", "statut_en_retard": "pas_actif"},
        {"tache": "export_hors_site", "noeud": null, "noeuds": [], "noeud_enregistre": false, "statut": "manquante",
         "periodicite_heures": 744}
      ],
      "anomalies_recentes": [{"tache": "telemetrie", "noeud": "nœud-a", "cree_le": "2026-11-10T00:15:00Z", "erreur": "Agrégation interrompue."}],
      "anomalies_fenetre_jours": 7,
      "genere_le": "2026-11-10T06:30:00Z"
    }"""

    fun tableau(
        kpi: String = """{"utilisateurs": 24, "vues": 146, "pages": 11, "heure_pointe": "18h", "moy_vues_utilisateur": 6.1}""",
        filtre: String = """{"propose": true, "gestionnaire_designe": true, "applique": "avec", "non_distingue_jusqu_au": null}""",
        canaux: Boolean = false,
    ) = """{
      "scope": "jour", "kpi": $kpi,
      "chart": [{"label": "8h", "total": 6, "uniques": 3}, {"label": "9h", "total": 14}],
      "chart_label": "Vues par heure",
      "top_pages": [{"page": "/tableau-de-bord", "total": 58, "uniques": 21${if (canaux) PAGE_CANAUX else ""}}],${if (canaux) CANAUX else ""}
      "top_users": [{"nom": "Camille EXEMPLE", "derniere_connexion": "2026-11-10T18:42:00Z", "statut": "copropriétaire_résident", "total": 34, "pages": 9}],
      "erreurs": [], "performance": {"indicateurs": [], "pages": []}, "gestes": [], "arrivees": [],
      "retour": {"comptes_mesures": 0, "refus": 0, "dormants": [], "liste_dormants": [],
        "arrivants": {"periode": "", "valides": 0, "en_attente": 0, "revenus": 0, "jamais_revenus": 0, "fenetre_jours": 0, "taux": null}},
      "filtre_gestionnaire": $filtre
    }"""
}
