// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.onglets

import fr.coprofirst.app.navigation.DroitsOnglet
import fr.coprofirst.app.navigation.OngletDuSite
import fr.coprofirst.app.navigation.OngletsDuSite
import fr.coprofirst.app.navigation.ouverts
import fr.coprofirst.app.fonctions.profil.AccesSimules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** La barre d'une page à onglets, comme l'iOS (`CadreAOnglets`, `AdminTests`). */
class OngletsDeLaPageTest {
    private val surLeSite = OngletDuSite(OngletsSurLeSite.ID, "Sur le site", null, true)
    private val conseil = DroitsOnglet.depuis(listOf("conseil_syndical"), null, "copropriétaire_résident", estModerateur = true, accesMenu = AccesSimules.CONSEIL)
    private val admin = DroitsOnglet.depuis(listOf("propriétaire", "admin"), null, "copropriétaire_résident", estModerateur = true, accesMenu = AccesSimules.ADMINISTRATEUR)
    private val operateur = DroitsOnglet.depuis(listOf("operateur"), null, "admin_technique", estModerateur = false, accesMenu = AccesSimules.TECHNIQUE)

    private fun page(id: String, portes: Set<String>, droits: DroitsOnglet, renvoieAuSite: Boolean = false) =
        OngletsDeLaPage(id, portes, renvoieAuSite, droits, surLeSite)

    @Test
    fun lesOngletsOuvertsDansLOrdreDuSite() {
        val onglets = page("espace-cs", setOf("badges"), conseil)
        assertEquals(OngletsDuSite.ouverts("espace-cs", conseil).map { it.id }, onglets.barre.map { it.id })
        // Un onglet non inscrit reste dans la barre : il dira « Bientôt ».
        assertTrue(onglets.barre.size > 1)
        assertEquals(emptyList<OngletDuSite>(), onglets.ailleurs)
    }

    @Test
    fun leChoixSuitLaBarre() {
        val onglets = page("espace-cs", emptySet(), conseil)
        assertEquals(onglets.barre.first(), onglets.actif(null))
        assertEquals(onglets.barre[1], onglets.actif(onglets.barre[1].id))
        // Un onglet que le compte n'ouvre plus (droits relus) : le premier.
        assertEquals(onglets.barre.first(), onglets.actif("disparu"))
    }

    @Test
    fun unePageReserveeNOffreAucunOnglet() {
        val resident = DroitsOnglet.depuis(listOf("résident"), null, "locataire", estModerateur = false, accesMenu = AccesSimules.RESIDENT)
        assertNull(page("espace-cs", setOf("validations"), resident).actif(null))
        assertNull(page("admin", setOf("utilisateurs"), conseil).actif(null))
    }

    @Test
    fun unePageQuiRenvoieAuSiteFinitParSurLeSite() {
        val onglets = page("admin", setOf("a_traiter", "utilisateurs", "telemetry", "maintenance"), admin, renvoieAuSite = true)
        assertEquals(listOf("a_traiter", "utilisateurs", "telemetry", "maintenance", OngletsSurLeSite.ID), onglets.barre.map { it.id })
        assertTrue(onglets.ailleurs.map { it.id }.containsAll(listOf("imports", "audit_lots")))
        assertTrue(onglets.ailleurs.none { it.id in setOf("a_traiter", "maintenance") })
    }

    @Test
    fun lOperateurNOuvreQueLExploitation() {
        val onglets = page("admin", setOf("a_traiter", "utilisateurs", "telemetry", "maintenance"), operateur, renvoieAuSite = true)
        assertEquals(listOf("maintenance", OngletsSurLeSite.ID), onglets.barre.map { it.id })
        assertEquals(listOf("installation", "installations"), onglets.ailleurs.map { it.id })
    }

    @Test
    fun sansOngletAilleursPasDeSurLeSite() {
        val tous = OngletsDuSite.ouverts("admin", operateur).map { it.id }.toSet()
        assertEquals(listOf("installation", "maintenance", "installations"), page("admin", tous, operateur, renvoieAuSite = true).barre.map { it.id })
    }

    @Test
    fun chaqueOngletDuSiteASonAdresseEtSonDescriptif() {
        OngletsDuSite.parPage.forEach { (page, onglets) ->
            onglets.forEach { onglet ->
                assertTrue("$page/${onglet.id} sans adresse", OngletsDuSite.routes[page]?.get(onglet.id)?.startsWith("/") == true)
                assertTrue("$page/${onglet.id} sans descriptif", !OngletsDuSite.descriptifs[page]?.get(onglet.id).isNullOrBlank())
            }
        }
    }

    @Test
    fun unOngletDuSiteSOuvreSousLAdresseDeLaResidence() {
        val route = "/admin?onglet=imports"
        assertEquals("https://residence.example.test/admin?onglet=imports", OngletsSurLeSite.lien(route, "https://residence.example.test"))
        assertEquals("https://residence.example.test/admin?onglet=imports", OngletsSurLeSite.lien(route, "https://residence.example.test/"))
        assertEquals("https://example.com/copro/admin?onglet=imports", OngletsSurLeSite.lien(route, "https://example.com/copro"))
        assertEquals("https://residence.example.test:8443/residence/carnet", OngletsSurLeSite.lien("/residence/carnet", "https://residence.example.test:8443"))
    }

    @Test
    fun uneRouteQuiNEstPasUnCheminNeMeneNullePart() {
        val base = "https://residence.example.test"
        assertNull(OngletsSurLeSite.lien("https://ailleurs.example.test/admin", base))
        assertNull(OngletsSurLeSite.lien("//ailleurs.example.test/admin", base))
        assertNull(OngletsSurLeSite.lien("javascript:alert(1)", base))
        assertNull(OngletsSurLeSite.lien("admin", base))
        assertNull(OngletsSurLeSite.lien("/admin", "http://residence.example.test"))
        assertNull(OngletsSurLeSite.lien("/admin", "pas une adresse"))
    }
}
