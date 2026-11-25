// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.communaute

import fr.coprofirst.api.modeles.AnnonceRead
import fr.coprofirst.app.commun.formats.FormatsDuSite
import fr.coprofirst.api.modeles.CategorieAnnonce
import fr.coprofirst.api.modeles.IdeeRead
import fr.coprofirst.api.modeles.StatutAnnonce
import fr.coprofirst.api.modeles.TypeAnnonce
import fr.coprofirst.app.commun.ConstantesDuServeur
import fr.coprofirst.app.navigation.DroitsOnglet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime

/** Les règles de la Communauté que l'app applique — celles du site et de l'iOS. */
class ReglesCommunauteTest {
    private val resident = DroitsOnglet.depuis(listOf("résident"), null, "copropriétaire_résident", estModerateur = false)
    private val syndic = DroitsOnglet.depuis(listOf("résident"), null, "syndic", estModerateur = false)

    @Test
    fun leMotifDuServeurFermeOuLaisseLAvis() {
        assertEquals(AccesCommunaute.Ouvert, AccesCommunaute.depuis(MoiCommunaute(1, resident, null)))
        // Une suspension ferme tout, mot pour mot.
        assertEquals(AccesCommunaute.Ferme("Suspendu jusqu'au 20/11."), AccesCommunaute.depuis(MoiCommunaute(1, resident, "Suspendu jusqu'au 20/11.")))
        // Un gestionnaire garde l'Avis, que la règle générée des onglets lui ouvre.
        assertEquals(AccesCommunaute.Ouvert, AccesCommunaute.depuis(MoiCommunaute(2, syndic, "Réservé aux résidents.")))
    }

    @Test
    fun lesSeuilsDeLAvisSontCeuxDuServeur() {
        assertTrue(ReglesCommunaute.demandeRemarque(ConstantesDuServeur.NOTE_MIN))
        assertFalse(ReglesCommunaute.demandeRemarque(ConstantesDuServeur.NOTE_REMARQUE))
        assertFalse(ReglesCommunaute.demandeRemarque(0))
        assertTrue(ReglesCommunaute.demandeConsentement(ConstantesDuServeur.NOTE_PUBLIABLE))
        assertFalse(ReglesCommunaute.demandeConsentement(ConstantesDuServeur.NOTE_PUBLIABLE - 1))
    }

    @Test
    fun laSaisieDUnAvisNEnvoieQueCeQueLaNoteDemande() {
        val basse = SaisieAvis(note = 2, titre = "T", description = "D", remarque = "  Lent  ", consentement = true)
        assertEquals("Lent", basse.remarqueEnvoyee)
        assertNull(basse.consentementEnvoye)
        assertTrue(basse.complete)
        assertFalse(basse.copy(remarque = " ").complete)

        val haute = SaisieAvis(note = 5, titre = "T", description = "D", remarque = "rien")
        assertNull(haute.remarqueEnvoyee)
        assertFalse("sans réponse sur la diffusion", haute.complete)
        assertTrue(haute.copy(consentement = false).complete)
        assertFalse("sans note", SaisieAvis(titre = "T", description = "D").complete)
    }

    @Test
    fun laSaisieDUneAnnonceLitLePrixALaFrancaise() {
        assertEquals(12.5, SaisieAnnonce(prix = " 12,5 ").prixLu!!, 0.0)
        assertTrue(SaisieAnnonce(titre = "T", description = "D").complete)
        assertFalse(SaisieAnnonce(titre = "T", description = "D", prix = "douze").complete)
        assertFalse(SaisieAnnonce(titre = "T", description = "D", prix = "-3").complete)
        // Un prix illisible ne bloque qu'une vente : il n'est pas envoyé pour un don.
        assertTrue(SaisieAnnonce(titre = "T", description = "D", type = "don", prix = "douze").complete)
        assertTrue(SaisieAnnonce(description = " Un lave-linge <neuf> ").descriptionHtml.startsWith("<p>Un lave-linge &lt;neuf"))
    }

    @Test
    fun lesIdeesLesPlusSoutenuesDAbordEtLeVoteSeVoitTouteDeSuite() {
        val idees = listOf(idee(1, 2, "ouverte"), idee(2, 9, "retenue"), idee(3, 5, "ouverte"))
        assertEquals(listOf(2, 3, 1), ReglesCommunaute.idees(idees, null).map { it.id })
        assertEquals(listOf(3, 1), ReglesCommunaute.idees(idees, "ouverte").map { it.id })
        val apres = ReglesCommunaute.basculerVote(idees, 1)
        assertEquals(true, apres[0].monVote)
        assertEquals(3, apres[0].nbVotes)
        val retire = ReglesCommunaute.basculerVote(apres, 1)
        assertEquals(false, retire[0].monVote)
        assertEquals(2, retire[0].nbVotes)
    }

    @Test
    fun lePrixAbsentPasseApresLesPrix() {
        // Les prix en centimes servis (#370) : 30,00 € et 5,00 €.
        val annonces = listOf(annonce(1, null, "2026-11-10"), annonce(2, 3000, "2026-11-12"), annonce(3, 500, "2026-11-11"))
        assertEquals(listOf(2, 3, 1), ReglesCommunaute.annonces(annonces, null, null, "recent").map { it.id })
        assertEquals(listOf(3, 2, 1), ReglesCommunaute.annonces(annonces, null, null, "prix_asc").map { it.id })
        assertEquals(listOf(2, 3, 1), ReglesCommunaute.annonces(annonces, null, null, "prix_desc").map { it.id })
        assertEquals(listOf(1), ReglesCommunaute.annonces(annonces, "don", null, "recent").map { it.id })
    }

    @Test
    fun lesCentimesServisFontLEuroExact() {
        // L'annonce rend `prix_centimes` par `FormatsDuSite.montant(centimes / 100.0)` (#370) : le
        // flottant de la division se relit comme le décimal exact des centimes, jusqu'au million d'euros.
        val nbsp = '\u00a0'
        assertEquals("40${nbsp}€", FormatsDuSite.montant(4000 / 100.0))
        assertEquals("0,07${nbsp}€", FormatsDuSite.montant(7 / 100.0))
        assertEquals("1\u202f234,56${nbsp}€", FormatsDuSite.montant(123456 / 100.0))
        val inexacts = (0..100_000_000 step 9_973).filter {
            java.math.BigDecimal((it / 100.0).toString()).compareTo(java.math.BigDecimal.valueOf(it.toLong(), 2)) != 0
        }
        assertEquals(emptyList<Int>(), inexacts)
    }

    @Test
    fun unCodeInconnuSeLitTelQuel() {
        assertEquals("🎁 Don", ReglesCommunaute.libelle("don", TablesCommunaute.typesAnnonce))
        assertEquals("troc", ReglesCommunaute.libelle("troc", TablesCommunaute.typesAnnonce))
        assertEquals("badge-gray", ReglesCommunaute.teinte("mystere", TablesCommunaute.statutsIdee).badge)
        assertEquals("recent", ReglesCommunaute.triParDefaut)
    }

    @Test
    fun leSondageSOuvreSurLeSiteSousLAdresseDeLaResidence() {
        assertEquals("https://residence.test/copro/sondages/7", lienSondage(7, "https://residence.test/copro"))
    }

    private fun idee(id: Int, votes: Int, statut: String) = IdeeRead(
        id = id, titre = "Idée $id", description = "", statut = statut,
        creeLe = OffsetDateTime.parse("2026-11-01T10:00:00Z"), perimetreLibelle = "", nbVotes = votes, monVote = false,
    )

    private fun annonce(id: Int, centimes: Int?, jour: String) = AnnonceRead(
        id = id, titre = "Annonce $id", description = "",
        typeAnnonce = if (centimes == null) TypeAnnonce.DON else TypeAnnonce.VENTE, categorie = CategorieAnnonce.DIVERS,
        statut = StatutAnnonce.EN_COURS, auteurId = 1, creeLe = OffsetDateTime.parse("${jour}T10:00:00Z"),
        perimetreLibelle = "", prixCentimes = centimes,
    )
}
