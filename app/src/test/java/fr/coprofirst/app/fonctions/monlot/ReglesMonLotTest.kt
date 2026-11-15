// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.monlot

import fr.coprofirst.app.commun.LibelleTeinte
import fr.coprofirst.app.navigation.DroitsOnglet
import fr.coprofirst.app.navigation.OngletsDuSite
import fr.coprofirst.app.navigation.RegistreDesMenus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Les règles de « Mes lots & accès » (#18), celles de l'iOS (`AccesTests`) : droits, libellés, regroupements. */
class ReglesMonLotTest {
    private fun droits(statut: String?, locataire: Boolean = false, cs: Boolean = false) =
        DroitsMonLot(statut, DroitsOnglet(locataire = locataire, cs = cs))

    @Test
    fun leMenuEstInscrit() = assertTrue("mon-lot" in RegistreDesMenus.ecrans)

    @Test
    fun leLocataireNeGerePasDeBaux() {
        val d = droits("locataire", locataire = true)
        assertTrue(d.locataire)
        assertNull(d.baux)
        assertFalse(d.voitLocation(bauxTermines = 3))
    }

    @Test
    fun leBailleurLitSesBauxEtVoitLaGestionLocative() {
        val d = droits("copropriétaire_bailleur")
        assertEquals(DroitsMonLot.LectureBaux.Siens, d.baux)
        assertTrue(d.voitLocation(bauxTermines = 0))
    }

    @Test
    fun leResidentNeVoitLaGestionLocativeQuAvecDesBauxTermines() {
        val d = droits("copropriétaire_résident")
        assertFalse(d.voitLocation(0))
        assertTrue(d.voitLocation(1))
    }

    /** Copropriétaire ET conseil : ses baux d'abord, comme `lireBaux` du site. */
    @Test
    fun leConseilLitTousLesBauxSIlNEstPasCoproprietaire() {
        assertEquals(DroitsMonLot.LectureBaux.Tous, droits("mandataire", cs = true).baux)
        assertEquals(DroitsMonLot.LectureBaux.Siens, droits("copropriétaire_bailleur", cs = true).baux)
    }

    /** « Nouvel accès » n'est offert qu'à qui a un lot : la route exige qu'il soit le sien. */
    @Test
    fun nouvelAccesExigeUnLot() {
        assertFalse(droits(null).peutDemander(emptyList()))
        assertTrue(droits(null).peutDemander(listOf(LotDuCompte(1, "1"))))
    }

    /** « Gestion locative » : ouverte par le site à tous, masquée à qui ne gère pas de bail. */
    @Test
    fun laGestionLocativeSeMasqueSansBail() {
        assertEquals(listOf("lots", "badges", "telecommandes"), droits("locataire", locataire = true).onglets(0).map { it.id })
        assertEquals(listOf("lots", "badges", "telecommandes", "location"), droits("copropriétaire_bailleur").onglets(0).map { it.id })
    }

    /** Les descriptifs générés et les onglets générés lisent le même `pages` : aucun orphelin. */
    @Test
    fun chaqueOngletASonDescriptif() {
        val ids = OngletsDuSite.parPage.getValue(DroitsMonLot.PAGE).map { it.id }.toSet()
        assertEquals(ids, TablesMonLot.descriptifs.keys)
    }

    @Test
    fun lesLibellesViennentDesTablesGenerees() {
        assertEquals(LibelleTeinte("Perdu", "badge-red"), LibellesMonLot.statutAcces("perdu"))
        assertEquals("En cours de sortie", LibellesMonLot.statutBail("en_cours_sortie").libelle)
        assertEquals("📡 Télécommande parking", LibellesMonLot.typeAcces("telecommande"))
        // Une valeur inconnue s'affiche brute : un libellé manquant doit se voir.
        assertEquals(LibelleTeinte("inconnu", "badge-gray"), LibellesMonLot.statutAcces("inconnu"))
        assertEquals("badge_rfid", LibellesMonLot.typeAcces("badge_rfid"))
    }

    /** `lotTypeLabel`, `etageLabel` ne sont pas recopiés : le libellé servi, sinon la donnée brute. */
    @Test
    fun leLotSAfficheCommeLApiLeSert() {
        val brut = LotDuCompte(417, "417", type = "cave", etage = -1, libelleTypeComplet = "", etageLibelle = "")
        assertEquals("cave", brut.typeLu)
        assertEquals("-1", brut.etageLu)
        assertEquals("— / 417", brut.intitule)
        assertEquals("—", LotDuCompte(1, "1").typeLu)
    }

    @Test
    fun lesConstatsSeRangentDuPlusGrave() {
        val rangs = LibellesMonLot.ordonner(
            listOf(ConstatDuLot("a", "information", ""), ConstatDuLot("b", "nouvelle", ""), ConstatDuLot("c", "bloquant", "")),
        ).map { it.code }
        assertEquals(listOf("c", "a", "b"), rangs)
    }

    @Test
    fun lesBauxSeGroupentParLocataire() {
        val baux = listOf(bail(3, locataire = 81), bail(4, locataire = null), bail(2, locataire = 81))
        assertEquals(listOf("81", "ext_4"), GroupesDeBaux.parLocataire(baux).map { it.cle })
        assertEquals(listOf(3, 2), GroupesDeBaux.parLocataire(baux)[0].baux.map { it.id })
    }

    @Test
    fun laVueParLocataireRattacheLesObjetsConfies() {
        val b = bail(3, locataire = 81, email = " Locataire@Exemple.test ")
        val autre = bail(5, locataire = 82, email = "locataire@exemple.test")
        val confie = AccesDetenu(1, "X", "actif", "vigik", chezLocataire = true, bailId = 3)
        val garde = AccesDetenu(2, "Y", "actif", "vigik", chezLocataire = false, bailId = 3)
        val groupes = GroupeAcces.grouper(listOf(b, autre), listOf(confie, garde))
        assertEquals("un même courriel, un même locataire", 1, groupes.size)
        assertEquals(listOf(3, 5), groupes[0].bauxIds)
        assertEquals(listOf(confie), groupes[0].objets)
    }

    @Test
    fun unAccesPerduLeResteSansRelire() {
        val lues = DonneesAcces(listOf(AccesDetenu(12, "A", "actif", "vigik")), listOf(AccesDetenu(21, "T", "actif", "telecommande")), emptyList())
        val apres = lues.marquerPerdu(lues.vigiks[0])
        assertEquals("perdu", apres.vigiks[0].statut)
        assertEquals("actif", apres.telecommandes[0].statut)
    }

    private fun bail(id: Int, locataire: Int?, email: String? = null) = Bail(
        id = id, lotId = 15, statut = "actif", locataireId = locataire, locatairePrenom = "Locataire",
        locataireNom = "du lot", locataireEmail = email, dateEntree = "2025-09-01",
    )
}
