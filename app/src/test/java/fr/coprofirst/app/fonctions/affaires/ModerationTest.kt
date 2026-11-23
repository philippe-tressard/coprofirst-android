// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.affaires

import fr.coprofirst.api.VERSION_CONTRAT_PLANCHER
import fr.coprofirst.app.commun.detail.sondage.ServiceSondageApi
import fr.coprofirst.app.commun.masquages.AuteurAMasquer
import fr.coprofirst.app.fonctions.communaute.ServiceCommunauteApi
import fr.coprofirst.app.session.Installation
import fr.coprofirst.app.session.SessionOuverte
import fr.coprofirst.app.session.SessionsParHote
import java.time.Instant
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** #282 : signaler, masquer — les offres d'une affaire et d'une suite, comme l'iOS (`ModerationTests`). */
class ModerationTest {
    private fun fiche(corriger: Boolean = false, archivee: Boolean = false) = DetailAffaire(
        id = 4, numero = "A-4", titre = "Fuite", description = "", categorie = "panne", statut = "ouvert", creeLe = Instant.EPOCH,
        droits = DroitsServisAffaire(corriger = corriger), archivee = archivee,
    )

    private fun suite(auteurId: Int? = 31, auteur: String? = "Dominique EXEMPLE", contenu: String? = "<p>Vu</p>") =
        SuiteAffaire(11, "commentaire", Instant.EPOCH, auteur = auteur, contenu = contenu, auteurId = auteurId)

    @Test
    fun leDrapeauSOffreAQuiLitSansCorrigerHorsArchivesSiLInstallationLeSert() {
        assertTrue(GesteAffaire.Signaler in GesteAffaire.offerts(fiche(), conseil = false, moderation = true))
        assertFalse("contrat absent ou illisible", GesteAffaire.Signaler in GesteAffaire.offerts(fiche(), conseil = false, moderation = false))
        assertFalse("qui corrige a d'autres gestes", GesteAffaire.Signaler in GesteAffaire.offerts(fiche(corriger = true), conseil = false, moderation = true))
        assertFalse("pas aux Archives", GesteAffaire.Signaler in GesteAffaire.offerts(fiche(archivee = true), conseil = false, moderation = true))
    }

    @Test
    fun uneSuiteDUnAutreSeSignaleEtSonAuteurSeMasque() {
        assertEquals(listOf(GesteSuite.Signaler, GesteSuite.Masquer), GesteSuite.offerts(suite(), moi = 7, moderation = true))
    }

    @Test
    fun niSaSuiteNiUneSuiteSansTexteNiSansContratNiSansCompteConnu() {
        assertEquals(emptyList<GesteSuite>(), GesteSuite.offerts(suite(auteurId = 7), moi = 7, moderation = true))
        assertEquals(emptyList<GesteSuite>(), GesteSuite.offerts(suite(contenu = null), moi = 7, moderation = true))
        assertEquals(emptyList<GesteSuite>(), GesteSuite.offerts(suite(), moi = 7, moderation = false))
        assertEquals(emptyList<GesteSuite>(), GesteSuite.offerts(suite(), moi = null, moderation = true))
    }

    /** Un auteur sans nom se signale, mais ne se masque pas : le geste nomme qui il masque. */
    @Test
    fun unAuteurSansNomNeSeMasquePas() {
        assertEquals(listOf(GesteSuite.Signaler), GesteSuite.offerts(suite(auteur = null), moi = 7, moderation = true))
    }

    @Test
    fun leChoixRetientLAuteurAMasquer() {
        val choix = ChoixDeSuite(moi = 7, moderation = true)
        choix.choisir(GesteSuite.Masquer, suite())
        assertEquals(AuteurAMasquer(31, "Dominique EXEMPLE"), choix.aMasquer)
        choix.choisir(GesteSuite.Signaler, suite())
        assertEquals(11, choix.aSignaler?.id)
    }

    @Test
    fun onNeSeMasquePasSoiMeme() {
        assertNull(AuteurAMasquer.de(7, "Moi", moi = 7))
        assertNull(AuteurAMasquer.de(null, "X", moi = 7))
        assertNull(AuteurAMasquer.de(31, " ", moi = 7))
        assertEquals(AuteurAMasquer(31, "X"), AuteurAMasquer.de(31, "X", moi = 7))
    }

    /**
     * #311 : au PLANCHER, la modération est offerte par défaut — sans porte. Le câblage lit
     * l'installation (`sertLePlancher`) : une installation au plancher ouvre 🚩 et 🙈 sur ce
     * qu'un autre a écrit, dans les fiches d'affaire, de sondage et la Communauté.
     */
    @Test
    fun auPlancherLaModerationEstOfferteParDefaut() {
        val installation = Installation("https://glycines.exemple.test", "Les Glycines", versionContrat = VERSION_CONTRAT_PLANCHER)
        val session = SessionOuverte(installation, SessionsParHote(OkHttpClient()))
        assertTrue("installation au plancher", installation.sertLePlancher)
        assertTrue("fiche d'un sondage", ServiceSondageApi(session).moderation)
        assertTrue("Communauté", ServiceCommunauteApi(session).moderation)
        val moderation = installation.sertLePlancher
        assertTrue(GesteAffaire.Signaler in GesteAffaire.offerts(fiche(), conseil = false, moderation = moderation))
        assertEquals(listOf(GesteSuite.Signaler, GesteSuite.Masquer), GesteSuite.offerts(suite(), moi = 7, moderation = moderation))
    }

    /** Le « ⋯ » d'un message garde ses deux gestes (#281) : ni signaler ni masquer n'y entrent. */
    @Test
    fun leMenuDUnMessageNeChangePas() {
        val message = MessageAffaire(41, "<p>Merci</p>", Instant.EPOCH, true, null, null, false)
        assertEquals(listOf(GesteSuite.Modifier, GesteSuite.Supprimer), message.gestesOfferts(fiche()))
    }
}
