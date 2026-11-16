// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.affaires

import fr.coprofirst.app.commun.formats.FormatsDuSite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/** Le fil d'une affaire, comme l'iOS (`FilAffaireTests`). */
class EtapesFilTest {
    private val t = TextesFil(
        demande = "La demande", suite = "La suite", comptesRendus = "Comptes rendus", synthese = "La synthèse",
        aucuneSuite = "Aucune suite pour l'instant.", aucunCompteRendu = "Aucun pour l'instant.",
        aucunCompteRenduClos = "Aucun compte rendu.", comptesRendusIllisibles = "Comptes rendus indisponibles.",
        aVenir = "À venir", brouillon = "Brouillon", csEtPresents = "CS et présents", partieSynthese = "Synthèse",
        difficultes = "Difficultés", amelioration = "Amélioration", laResidence = "La résidence",
        suites = { "$it suite(s)" }, points = { "$it point(s)" }, dernierPoint = { "Dernier point le $it" },
        point = { "Point du $it" }, validee = { "Validée le $it" }, le = { e, d -> "$e le $d" },
    )
    private val servis = ReferentielsAffaires(
        emptyList(),
        listOf(
            ReferentielsAffaires.Statut("ouvert", "Ouvert", false, "cs", true),
            ReferentielsAffaires.Statut("en_cours", "Chez le syndic", false, "syndic", true),
        ),
        emptyList(),
    )
    private val creeLe = Instant.parse("2026-11-02T09:00:00Z")

    private fun affaire(
        categorie: String = "panne",
        close: Boolean = false,
        suites: List<SuiteAffaire> = emptyList(),
        cr: List<CompteRenduAffaire>? = emptyList(),
        synthese: SyntheseAffaire? = null,
    ) = DetailAffaire(
        id = 1, numero = "A-0001", titre = "Fuite", description = "<p>Une fuite</p>", categorie = categorie, statut = "ouvert",
        creeLe = creeLe, auteur = "Le conseil syndical", suites = suites, close = close, referentiels = servis,
        comptesRendus = cr, synthese = synthese,
    )

    private fun suite(id: Int, jour: Int, ancien: String? = null, nouveau: String? = null, contenu: String? = null, type: String = "etat") =
        SuiteAffaire(id, type, Instant.parse("2026-11-0${jour}T10:00:00Z"), "Syndic", contenu, ancien, nouveau)

    @Test
    fun uneAffaireOuverteAQuatreSectionsEtUneSyntheseAVenir() {
        val etapes = EtapeFil.du(affaire(), t)
        assertEquals(listOf(EtapeFil.Cle.Demande, EtapeFil.Cle.Suite, EtapeFil.Cle.ComptesRendus, EtapeFil.Cle.Synthese), etapes.map { it.cle })
        assertEquals("${FormatsDuSite.date(creeLe)} · Le conseil syndical", etapes[0].meta)
        assertEquals(EtapeFil.Etat.AVenir, etapes[1].etat)
        assertEquals("Aucune suite pour l'instant.", etapes[1].meta)
        assertEquals("À venir", etapes[3].meta)
    }

    @Test
    fun uneActualiteNAQueLaDemandeEtLaSuite() {
        assertEquals(2, EtapeFil.du(affaire(categorie = "actualite"), t).size)
    }

    @Test
    fun laSuiteResumeSaDerniereEntreeEtTaitLaSynthese() {
        val etape = EtapeFil.suite(
            affaire(suites = listOf(suite(1, 3, "ouvert", "en_cours", "<p>Le syndic est <b>prévenu</b>.</p>"), suite(2, 4, type = "synthese"))), t,
        )
        assertEquals(EtapeFil.Etat.EnCours, etape.etat)
        assertEquals(1, etape.elements.size)
        assertEquals("Ouvert → Chez le syndic", etape.elements.single().titre)
        val le = FormatsDuSite.date(Instant.parse("2026-11-03T10:00:00Z"))
        assertEquals("Chez le syndic le $le — Le syndic est prévenu.", etape.resumePlie)
        assertEquals(true, etape.ouverteALaSuite && etape.pliable)
    }

    @Test
    fun uneEntreeSansChangementDEtatPortelNomDeSonAuteur() {
        val element = EtapeFil.element(suite(1, 3, contenu = "<p>Bonjour</p>").copy(auteur = null), servis, t)
        assertEquals("La résidence", element.titre)
        assertEquals("Bonjour", element.apercu)
    }

    @Test
    fun lesComptesRendusIllisiblesSeDisent() {
        val etape = EtapeFil.comptesRendus(null, false, t)
        assertEquals("Comptes rendus indisponibles.", etape.meta)
        assertEquals(false, etape.pliable)
        val lus = EtapeFil.comptesRendus(listOf(CompteRenduAffaire(7, creeLe, "En salle", "<p>Vu</p>", true)), false, t)
        assertEquals("1 point(s)", lus.meta)
        assertEquals("En salle · CS et présents", lus.elements.single().ligne)
    }

    @Test
    fun uneAffaireCloseSansSyntheseNEnMontrePas() {
        assertNull(EtapeFil.synthese(affaire(close = true), t))
        val validee = EtapeFil.synthese(affaire(close = true, synthese = SyntheseAffaire("validee", "<p>Réparé</p>", null, "  ", creeLe)), t)
        assertEquals(EtapeFil.Etat.Realise, validee?.etat)
        assertEquals(listOf("synthese.texte"), validee?.elements?.map { it.id })
    }

    @Test
    fun leTraitDuRail() {
        val etats = listOf(EtapeFil.Etat.Realise, EtapeFil.Etat.EnCours, EtapeFil.Etat.AVenir)
        assertEquals(TraitDuRail.Engage, TraitDuRail.sous(0, etats))
        assertEquals(TraitDuRail.Discret, TraitDuRail.sous(1, etats))
        assertNull(TraitDuRail.sous(2, etats))
        assertEquals(TraitDuRail.Pointille, TraitDuRail.sous(2, etats, ouvert = true))
    }

    @Test
    fun lEtatDUneActualiteEstSonSuivi() {
        assertEquals(EtatServi("ouvert", "Ouvert"), affaire().etat)
        assertNull(affaire(categorie = "actualite").copy(suiviActualiteLibelle = "").etat)
        assertEquals(
            EtatServi("a_suivre", "À suivre"),
            affaire(categorie = "actualite").copy(suiviActualite = "a_suivre", suiviActualiteLibelle = "À suivre").etat,
        )
    }

    @Test
    fun lesIntervenantsPortentLeRythmeApresLeDernier() {
        assertEquals(listOf("Ascenseurs du Parc · ↺ Mensuel"), affaire().copy(prestataires = listOf(PrestataireAffaire(3, "Ascenseurs du Parc")), rythme = "↺ Mensuel").intervenants)
        assertEquals(emptyList<String>(), affaire().copy(rythme = "↺ Mensuel").intervenants)
    }
}
