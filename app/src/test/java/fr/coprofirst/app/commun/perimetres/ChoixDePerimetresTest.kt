// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.perimetres

import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le choix des périmètres d'une annonce Hall (`PerimetrePicker.svelte`), comme
 * `ChoixDePerimetresTests` de l'iOS : des têtes, puis les espaces d'une seule — et la
 * règle en cinq lignes du site, sur l'arbre simulé lu par le client généré (un défaut, le
 * regroupement « Bâtiments », trois espaces sous A).
 */
class ChoixDePerimetresTest {
    private val arbre = ArbreDesPerimetres(lus)

    // — Les tests de l'iOS, ligne à ligne —

    @Test
    fun lesTetesSontLesBatimentsEtLesLieuxPasLeRegroupementNiLeDefaut() {
        assertEquals("copropriete", arbre.defaut?.code)
        assertEquals(listOf("bat_a", "bat_b", "parking", "jardin"), arbre.tetes.map { it.code })
        assertEquals(listOf("bat_a/hall", "bat_a/toit", "bat_a_terrasses"), arbre.espaces("bat_a").map { it.code })
        // Les terrasses, au niveau 3, n'ouvrent pas un niveau de plus.
        assertTrue(arbre.espaces("bat_a_terrasses").isEmpty())
        assertEquals("bat_a", arbre.tete("bat_a/toit"))
    }

    /** #283 : le `mode="single"` du site (le lieu d'un écran de hall) — un toucher remplace, jamais n'ajoute. */
    @Test
    fun enChoixUniqueUnToucherRemplaceLaSelection() {
        var choix = ChoixDePerimetres(unique = true).choisirDefaut(arbre)
        choix = choix.basculer("bat_a", arbre)
        assertTrue(choix.codes == setOf("bat_a") && choix.ouverte(arbre) == "bat_a")
        choix = choix.basculer("bat_a/toit", arbre)
        assertTrue(choix.codes == setOf("bat_a/toit") && choix.ouverte(arbre) == "bat_a")
        choix = choix.basculer("parking", arbre)
        assertEquals("le parking remplace le toit, il ne s'y ajoute pas", setOf("parking"), choix.codes)
        choix = choix.basculer("parking", arbre)
        assertEquals("toucher la valeur retenue la garde : jamais vide", setOf("parking"), choix.codes)
        choix = choix.choisirDefaut(arbre)
        assertEquals(setOf("copropriete"), choix.codes)
        assertTrue("le défaut garde le mode", choix.unique)
    }

    @Test
    fun uneTeteSAjouteEtOuvreSesEspacesPuisSeRetireEtLesReferme() {
        var choix = ChoixDePerimetres().choisirDefaut(arbre)
        assertTrue(choix.estDefaut(arbre) && choix.ouverte(arbre) == null)
        choix = choix.basculer("bat_a", arbre)
        assertEquals(setOf("bat_a"), choix.codes)
        assertEquals("bat_a", choix.ouverte(arbre))
        choix = choix.basculer("bat_a", arbre)
        assertEquals("vide : retour au défaut", setOf("copropriete"), choix.codes)
        assertNull(choix.ouverte(arbre))
    }

    @Test
    fun unEspaceRemplaceSonBatimentEtRevenirSurLeBatimentNeLEfacePas() {
        var choix = ChoixDePerimetres().basculer("bat_a", arbre).basculer("bat_a/toit", arbre)
        assertEquals("« le toit du bâtiment A », pas les deux", setOf("bat_a/toit"), choix.codes)
        choix = choix.basculer("parking", arbre)
        // Rouvrir le bâtiment A pour vérifier son toit ne change rien à la sélection.
        choix = choix.basculer("bat_a", arbre)
        assertEquals(setOf("bat_a/toit", "parking"), choix.codes)
        assertEquals("bat_a", choix.ouverte(arbre))
        assertEquals(listOf("bat_a/toit"), choix.espacesRetenus("bat_a", arbre).map { it.code })
    }

    @Test
    fun leDefautVideToutLeReste() {
        val choix = ChoixDePerimetres().basculer("bat_b", arbre).basculer("jardin", arbre).choisirDefaut(arbre)
        assertEquals(setOf("copropriete"), choix.codes)
        assertTrue(choix.estDefaut(arbre))
    }

    // — La table `basculer`, une ligne par cas —

    @Test
    fun ligne1UneTeteQuiADesEspacesRetenusSOuvreSansRienChanger() {
        val avant = ChoixDePerimetres().basculer("bat_a/hall", arbre).basculer("parking", arbre)
        val apres = avant.basculer("bat_a", arbre)
        assertEquals(avant.codes, apres.codes)
        assertEquals("bat_a", apres.touchee)
        assertEquals("bat_a", apres.ouverte(arbre))
    }

    @Test
    fun ligne2UneTeteSansEspaceRetenuNonRetenueSAjouteEtSOuvre() {
        val choix = ChoixDePerimetres().basculer("parking", arbre).basculer("bat_a", arbre)
        assertEquals(setOf("parking", "bat_a"), choix.codes)
        assertEquals("bat_a", choix.ouverte(arbre))
    }

    @Test
    fun ligne3UneTeteRetenueSeRetireEtSaRangeeSeFerme() {
        val choix = ChoixDePerimetres().basculer("parking", arbre).basculer("bat_a", arbre).basculer("bat_a", arbre)
        assertEquals(setOf("parking"), choix.codes)
        assertNull(choix.touchee)
        assertNull("le parking n'a pas d'espaces", choix.ouverte(arbre))
    }

    @Test
    fun ligne4UnEspaceBasculeRetireSaTeteEtLaRangeeResteOuverte() {
        var choix = ChoixDePerimetres().basculer("bat_a", arbre).basculer("bat_a/hall", arbre)
        assertEquals(setOf("bat_a/hall"), choix.codes)
        assertEquals("bat_a", choix.ouverte(arbre))
        choix = choix.basculer("bat_a/toit", arbre)
        assertEquals(setOf("bat_a/hall", "bat_a/toit"), choix.codes)
        choix = choix.basculer("bat_a/hall", arbre)
        assertEquals(setOf("bat_a/toit"), choix.codes)
        assertEquals("bat_a", choix.ouverte(arbre))
        choix = choix.basculer("bat_a/toit", arbre)
        assertEquals("le dernier espace retiré : retour au défaut", setOf("copropriete"), choix.codes)
        assertEquals("la rangée reste ouverte", "bat_a", choix.ouverte(arbre))
    }

    @Test
    fun ligne5LeDefautVideToutEtFermeLaRangee() {
        val choix = ChoixDePerimetres().basculer("bat_a", arbre).basculer("bat_a/toit", arbre).choisirDefaut(arbre)
        assertEquals(setOf("copropriete"), choix.codes)
        assertNull(choix.touchee)
        assertNull(choix.ouverte(arbre))
    }

    @Test
    fun leDefautRetenuAvecUneTeteEstRetireParLeToucher() {
        val choix = ChoixDePerimetres(setOf("copropriete")).basculer("jardin", arbre)
        assertEquals(setOf("jardin"), choix.codes)
        assertFalse(choix.estDefaut(arbre))
    }

    @Test
    fun laRangeeOuverteSansToucherEstCelleDUneValeurRetenue() {
        // Un contenu qu'on modifie : rien de touché, le toit du bâtiment A retenu.
        assertEquals("bat_a", ChoixDePerimetres(setOf("parking", "bat_a/toit")).ouverte(arbre))
        assertNull(ChoixDePerimetres(setOf("parking", "jardin")).ouverte(arbre))
    }

    // — Ce que la pastille montre —

    @Test
    fun laTetePorteSesEspacesRetenusEnLibelleCourt() {
        val bat = arbre.noeud("bat_a")!!
        var choix = ChoixDePerimetres().basculer("bat_a/toit", arbre)
        assertEquals("Bâtiment A › Toit", choix.libelleDeTete(bat, arbre))
        choix = choix.basculer("bat_a/hall", arbre)
        assertEquals("dans l'ordre de l'arbre", "Bâtiment A › Hall ·\u00A0Toit", choix.libelleDeTete(bat, arbre))
        assertTrue(choix.teteRetenue("bat_a", arbre))
        assertFalse(choix.teteRetenue("bat_b", arbre))
        assertEquals("Bâtiment B", choix.libelleDeTete(arbre.noeud("bat_b")!!, arbre))
        assertFalse("le défaut seul : aucune tête pleine", ChoixDePerimetres().teteRetenue("bat_a", arbre))
    }

    @Test
    fun lAideEstLaDescriptionDuSeulCodeRetenu() {
        val aide = "Notifie l'ensemble du conseil syndical."
        val arbre = ArbreDesPerimetres(lus.map { if (it.code == "parking") it.copy(description = aide) else it })
        assertEquals(aide, ChoixDePerimetres(setOf("parking")).aide(arbre))
        assertNull("deux codes : pas d'aide", ChoixDePerimetres(setOf("parking", "jardin")).aide(arbre))
        assertNull("description vide : pas d'aide", ChoixDePerimetres(setOf("jardin")).aide(arbre))
    }

    // — Les têtes sous un regroupement —

    @Test
    fun lesEnfantsDUnRegroupementSontDesTetesPasCeuxDUneRacineCiblable() {
        val arbre = ArbreDesPerimetres(
            listOf(
                Perimetre("tout", "Tout", parDefaut = true),
                Perimetre("exterieurs", "Extérieurs", selectionnable = false),
                Perimetre("cour", "Cour", profondeur = 1, parent = "exterieurs"),
                Perimetre("cour/bancs", "Bancs", profondeur = 2, parent = "cour"),
                Perimetre("cave", "Cave"),
                Perimetre("cave/box", "Box", profondeur = 1, parent = "cave"),
            ),
        )
        assertEquals(listOf("cour", "cave"), arbre.tetes.map { it.code })
        assertEquals("cour", arbre.tete("cour/bancs"))
        assertEquals("cave", arbre.tete("cave/box"))
        assertNull("le regroupement n'est la tête de rien", arbre.tete("exterieurs"))
        assertEquals(listOf("cave/box"), arbre.espaces("cave").map { it.code })
    }

    @Test
    fun unArbreSansDefautNeVideJamaisVersUnCodeInconnu() {
        val arbre = ArbreDesPerimetres(listOf(Perimetre("cave", "Cave")))
        assertNull(arbre.defaut)
        val choix = ChoixDePerimetres().basculer("cave", arbre).basculer("cave", arbre)
        assertTrue(choix.codes.isEmpty())
        assertTrue(choix.estDefaut(arbre))
    }

    // — `aSaisir` —

    @Test
    fun aSaisirSuitLesNiveauxLibresServis() {
        val codes = Perimetre.aSaisir(lus).map { it.code }
        assertFalse("le regroupement ne se cible pas", "batiments" in codes)
        assertFalse("sous un parent sans niveau libre", "bat_a_terrasse_nord" in codes)
        assertTrue("bat_a_terrasses" in codes && "bat_a/toit" in codes && "copropriete" in codes)
    }

    @Test
    fun aSaisirGardeLeLieuDejaEnregistreMemeTropProfond() {
        val codes = Perimetre.aSaisir(lus, garder = "bat_a_terrasse_nord").map { it.code }
        assertTrue("bat_a_terrasse_nord" in codes)
        assertTrue("garder ne rend pas ciblable un regroupement", "batiments" !in codes)
    }

    @Test
    fun aSaisirSansNiveauxLibresServisProposeToutNoeudSelectionnable() {
        val sansNiveaux = lus.map { it.copy(niveauxLibres = null) }
        val codes = Perimetre.aSaisir(sansNiveaux).map { it.code }
        assertTrue("bat_a_terrasse_nord" in codes)
        assertFalse("batiments" in codes)
    }

    companion object {
        private val banc = BancPerimetres()

        /** L'arbre servi, lu une fois par le client généré. */
        private val lus: List<Perimetre> = banc.arbre()

        @JvmStatic
        @AfterClass
        fun arreter() = banc.close()
    }
}
