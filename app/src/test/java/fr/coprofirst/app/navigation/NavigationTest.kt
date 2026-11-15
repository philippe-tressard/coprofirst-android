// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.navigation

import fr.coprofirst.app.charte.Pictogrammes
import fr.coprofirst.app.session.AdresseInstallation
import fr.coprofirst.app.session.Installation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** La barre de la résidence, comme l'iOS : l'ordre servi, les libellés, les droits. */
class NavigationTest {
    private val ids = PagesParDefaut.toutes.map { it.id }

    @Test
    fun ordreServiPuisLeReste() {
        val ordre = OrdrePages.ordonner(PagesParDefaut.toutes, listOf("mes-demandes", "inconnue", "residence"), emptyMap())
        assertEquals(listOf("mes-demandes", "residence"), ordre.take(2).map { it.id })
        assertEquals(ids.toSet(), ordre.map { it.id }.toSet())
    }

    @Test
    fun ordreLuEnJsonOuEnListe() {
        assertEquals(listOf("a", "b"), OrdrePages.lire("""["a","b"]"""))
        assertEquals(listOf("a", "b"), OrdrePages.lire(" a , b "))
        assertEquals(null, OrdrePages.lire(""))
    }

    @Test
    fun libellesCourtsEtSurcharges() {
        val pages = OrdrePages.ordonner(
            PagesParDefaut.toutes, null,
            mapOf("residence" to SurchargePage(navLabel = "Notre immeuble"), "faq" to SurchargePage(titre = "Questions")),
        ).associateBy { it.id }
        assertEquals("Notre immeuble", pages.getValue("residence").libelle)
        assertEquals("Questions", pages.getValue("faq").titre)
        assertEquals("Affaires", pages.getValue("mes-demandes").libelle)
        assertEquals(PagesParDefaut.TITRE_DU_FIL, pages.getValue(LibellesBarre.ID_FIL).libelle)
    }

    @Test
    fun laConfigServieRegleLaBarre() {
        val adresse = AdresseInstallation.normaliser("residence.exemple.fr")!!
        val installation = Installation.depuisConfig(
            adresse,
            mapOf("site_nom" to "R", "pages_order" to """["faq"]""", "page_config_faq" to """{"navLabel":"Aide"}"""),
        )
        assertEquals("faq", installation.pages.first().id)
        assertEquals("Aide", installation.pages.first().libelle)
    }

    @Test
    fun droitsLusDuCompte() {
        val resident = DroitsOnglet.depuis(listOf("résident"), null, "locataire", estModerateur = false)
        assertTrue(resident.locataire)
        assertFalse(resident.outilDuConseil)
        val conseil = DroitsOnglet.depuis(listOf("conseil_syndical"), null, "copropriétaire_résident", estModerateur = true)
        assertTrue(conseil.outilDuConseil && conseil.proprioOuCS)
        // Le compte technique : administrateur sans lot ni conseil, pas d'outils du conseil.
        val technique = DroitsOnglet.depuis(listOf("admin"), null, "admin_technique", estModerateur = true)
        assertTrue(technique.adminSeul && !technique.outilDuConseil && technique.administrateur)
        assertTrue(DroitsOnglet.depuis(emptyList(), "propriétaire", "aidant", false).aidant)
    }

    @Test
    fun pagesOuvertesSelonLesDroits() {
        val resident = PagesOuvertes.filtrer(PagesParDefaut.toutes, DroitsOnglet()).map { it.id }
        assertFalse("espace-cs" in resident || "prestataires" in resident || "admin" in resident || "delegations" in resident)
        val conseil = PagesOuvertes.filtrer(PagesParDefaut.toutes, DroitsOnglet(cs = true)).map { it.id }
        assertTrue("espace-cs" in conseil && "prestataires" in conseil && "delegations" in conseil)
    }

    @Test
    fun ongletsReservesAuxAdministrateurs() {
        val admin = OngletsDuSite.ouverts("admin", DroitsOnglet(administrateur = true))
        val operateur = OngletsDuSite.ouverts("admin", DroitsOnglet(operateur = true))
        assertTrue(admin.size > operateur.size)
        assertTrue(operateur.all { it.ouvertOperateur })
        assertEquals(emptyList<OngletDuSite>(), OngletsDuSite.ouverts("admin", DroitsOnglet()))
    }

    @Test
    fun chaquePageGenereeASonIcone() {
        PagesParDefaut.toutes.forEach { assertTrue("icône sans pendant : ${it.icone}", it.icone in Pictogrammes.parNom) }
    }
}
