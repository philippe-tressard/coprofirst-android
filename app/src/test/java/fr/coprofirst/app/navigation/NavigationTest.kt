// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.navigation

import fr.coprofirst.api.infrastructure.Serializer
import fr.coprofirst.api.modeles.UserRead
import fr.coprofirst.app.charte.Pictogrammes
import fr.coprofirst.app.fonctions.profil.AccesSimules
import fr.coprofirst.app.fonctions.profil.FixturesProfil
import fr.coprofirst.app.session.AdresseInstallation
import fr.coprofirst.app.session.Installation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
        val adresse = AdresseInstallation.normaliser("residence.exemple.test")!!
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
        val conseil = DroitsOnglet.depuis(listOf("conseil_syndical"), null, "copropriétaire_résident", estModerateur = true)
        assertTrue(conseil.cs && conseil.proprioOuCS)
        val technique = DroitsOnglet.depuis(listOf("admin"), null, "admin_technique", estModerateur = true)
        assertTrue(technique.administrateur)
    }

    /** `acces_menu` se lit de GET /auth/me (#333) ; un compte gardé qui ne le porte pas a les pages de tous. */
    @Test
    fun lesPublicsDuMenuSeLisentDuCompte() {
        fun moi(acces: String?) = Serializer.kotlinxSerializationJson.decodeFromString<UserRead>(
            FixturesProfil.MOI.replaceFirst("{", acces?.let { """{"acces_menu": $it, """ } ?: "{"),
        )
        assertEquals(setOf("tous", "resident", "cs"), DroitsOnglet.depuis(moi("""["tous", "resident", "cs"]""")).accesMenu)
        assertEquals(setOf("tous"), DroitsOnglet.depuis(moi(null)).accesMenu)
        assertEquals(emptySet<String>(), DroitsOnglet.depuis(moi("[]")).accesMenu)
    }

    /**
     * #333, comme `PagesOuvertesTests` de l'iOS : une page s'affiche si son public (`acces`, généré
     * de la table `pages`) est dans `acces_menu` servi — aucun rôle n'est lu. Ce que le serveur a
     * tranché (coprofirst#3003) : l'aidant n'a pas la Communauté, le compte technique n'a qu'Admin.
     */
    @Test
    fun pagesOuvertesSelonLePublicServi() {
        fun ouvertes(acces: Set<String>) = PagesOuvertes.filtrer(PagesParDefaut.toutes, DroitsOnglet(accesMenu = acces)).map { it.id }
        val resident = ouvertes(AccesSimules.RESIDENT)
        assertTrue("communaute" in resident && LibellesBarre.ID_FIL in resident)
        assertFalse("espace-cs" in resident || "prestataires" in resident || "admin" in resident || "delegations" in resident)
        val conseil = ouvertes(AccesSimules.CONSEIL)
        assertTrue("espace-cs" in conseil && "prestataires" in conseil && "delegations" in conseil)
        assertFalse("admin" in conseil)
        assertEquals(listOf("admin"), ouvertes(AccesSimules.TECHNIQUE))
        val aidant = ouvertes(AccesSimules.AIDANT)
        assertTrue("delegations" in aidant)
        assertFalse("communaute" in aidant || "espace-cs" in aidant)
        // Avant la relecture du compte : les pages de tous seulement.
        assertEquals(PagesParDefaut.toutes.filter { it.acces == PagesOuvertes.TOUS }.map { it.id }, ouvertes(DroitsOnglet().accesMenu))
        assertEquals(emptyList<String>(), ouvertes(emptySet()))
        // Une page inconnue, ou hors du menu, reste fermée.
        assertFalse(PagesOuvertes.ouverte("inconnue", DroitsOnglet(accesMenu = AccesSimules.CONSEIL)))
        assertFalse(PagesOuvertes.ouverte(PageDeNavigation("profil", "", "Profil", "Profil", "user"), DroitsOnglet()))
    }

    /** L'accueil de la barre (#333) : le fil s'il y est, sinon la première page servie ; rien sur une barre vide. */
    @Test
    fun lAccueilDeLaBarre() {
        fun barre(acces: Set<String>) = PagesOuvertes.filtrer(PagesParDefaut.toutes, DroitsOnglet(accesMenu = acces))
        assertEquals(LibellesBarre.ID_FIL, OrdrePages.accueil(barre(AccesSimules.RESIDENT)))
        assertEquals(LibellesBarre.ID_FIL, OrdrePages.accueil(barre(AccesSimules.RESIDENT).reversed()))
        assertEquals("admin", OrdrePages.accueil(barre(AccesSimules.TECHNIQUE)))
        assertNull(OrdrePages.accueil(emptyList()))
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
