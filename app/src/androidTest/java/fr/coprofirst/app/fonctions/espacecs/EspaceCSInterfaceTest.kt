// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.espacecs

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.coprofirst.app.ThemeCoproFirst
import fr.coprofirst.app.commun.Apparence
import fr.coprofirst.app.commun.GestesDeSortie
import fr.coprofirst.app.commun.StockageApparence
import fr.coprofirst.app.commun.onglets.ReperesOnglets
import fr.coprofirst.app.commun.toucherTag
import fr.coprofirst.app.navigation.BarreDuHaut
import fr.coprofirst.app.navigation.ContexteDeMenu
import fr.coprofirst.app.navigation.DroitsOnglet
import fr.coprofirst.app.navigation.PagesParDefaut
import fr.coprofirst.app.session.ClientHttp
import fr.coprofirst.app.session.Installation
import fr.coprofirst.app.session.SessionOuverte
import fr.coprofirst.app.session.SessionsParHote
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * L'Espace conseil syndical (#24) à l'écran, comme l'iOS (`EspaceCSUITests`) : les six
 * onglets du site, dans son ordre ; approuver un compte en nouvel arrivant, accepter une
 * commande avec le code remis, chercher un badge — contre la résidence simulée, servie en
 * HTTPS à l'app par son vrai client. Les repères sont les identifiants de l'iOS.
 */
@RunWith(AndroidJUnit4::class)
class EspaceCSInterfaceTest {
    /**
     * La règle v2 : ses effets tournent sur un `StandardTestDispatcher`, au fil principal.
     * La v1 (dépréciée) les menait sur un `UnconfinedTestDispatcher` : un chargement
     * (`Chargement`, revenu du fil d'E/S) ou une animation (les barres du Reporting)
     * reprenait sur un autre fil, et Compose y observait des lectures — « Detected
     * multithreaded access to SnapshotStateObserver », par intermittence (CI de #80).
     */
    @get:Rule
    val ecran = createComposeRule()
    private val residence = ResidenceSimuleeCS()
    private val certificat = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
    private val serveur = MockWebServer().apply {
        useHttps(HandshakeCertificates.Builder().heldCertificate(certificat).build().sslSocketFactory())
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val chemin = request.url.encodedPath + request.url.encodedQuery?.let { "?$it" }.orEmpty()
                val (code, corps) = residence.repondre(request.method, chemin, request.body?.utf8().orEmpty())
                return MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(corps).build()
            }
        }
        start()
    }

    @Before
    fun ouvrir() {
        val confiance = HandshakeCertificates.Builder().addTrustedCertificate(certificat.certificate).build()
        val client = ClientHttp.partage.newBuilder().sslSocketFactory(confiance.sslSocketFactory(), confiance.trustManager).build()
        val session = SessionOuverte(Installation("https://residence.test", "Les Glycines"), SessionsParHote(client)) {
            serveur.url("/api").toString()
        }
        val contexte = ContexteDeMenu(
            page = PagesParDefaut.toutes.first { it.id == "espace-cs" },
            session = session,
            droits = DroitsOnglet.depuis(listOf("conseil_syndical"), null, "copropriétaire_résident", estModerateur = true),
            sortie = object : GestesDeSortie {
                override fun deconnecter() = Unit
                override fun oublierTout() = Unit
            },
            apparence = Apparence(object : StockageApparence {
                override fun lire(): String? = null
                override fun ecrire(valeur: String) = Unit
            }),
            barre = BarreDuHaut(),
        )
        ecran.setContent { ThemeCoproFirst { EcranEspaceCS(contexte) } }
    }

    @After
    fun fermer() = serveur.close()

    private fun attendre(texte: String) = ecran.waitUntil(DELAI_MS) {
        ecran.onAllNodes(hasText(texte, substring = true)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun attendreRepere(repere: String) =
        ecran.waitUntil(DELAI_MS) { ecran.onAllNodes(hasTestTag(repere)).fetchSemanticsNodes().isNotEmpty() }

    private fun disparu(repere: String) =
        ecran.waitUntil(DELAI_MS) { ecran.onAllNodes(hasTestTag(repere)).fetchSemanticsNodes().isEmpty() }

    @Test
    fun lesSixOngletsDuSite() {
        listOf("validations", "badges", "reporting", "annonces-hall", "annuaire", "courriels").forEach {
            attendreRepere(ReperesOnglets.onglet(it))
        }
    }

    @Test
    fun approuverUnNouvelArrivantPuisAccepterUneCommande() {
        attendreRepere(ReperesEspaceCS.compte(501))
        attendreRepere(ReperesEspaceCS.COMPTEUR_COMPTES)
        ecran.toucherTag("${ReperesEspaceCS.compte(501)}.approuver")
        ecran.toucherTag(ReperesEspaceCS.NOUVEL_ARRIVANT)
        attendre("Ancien résident")
        ecran.toucherTag(ReperesEspaceCS.VALIDATION_ENREGISTRER)
        disparu(ReperesEspaceCS.compte(501))
        attendre("Compte activé — 1 lot rattaché.")
        assertTrue(residence.recues.any { it.startsWith("POST /api/admin/utilisateurs/501/accueil-arrivant") })

        ecran.toucherTag("${ReperesEspaceCS.commande(71)}.approuver")
        ecran.toucherTag(ReperesEspaceCS.COMMANDE_CODES)
        ecran.onNodeWithTag(ReperesEspaceCS.COMMANDE_CODES).performTextInput("4521")
        ecran.toucherTag(ReperesEspaceCS.COMMANDE_ACCEPTER)
        disparu(ReperesEspaceCS.commande(71))
        attendre("Commande approuvée")
        assertTrue(residence.recues.any { it.contains("/admin/commandes-acces/71/traiter") && it.contains("\"4521\"") })
    }

    @Test
    fun rejeterUnCompte() {
        ecran.toucherTag("${ReperesEspaceCS.compte(503)}.rejeter")
        attendre("Rejeter le compte de Alix MODÈLE ?")
        ecran.toucherTag(ReperesEspaceCS.REJET_CONFIRMER)
        disparu(ReperesEspaceCS.compte(503))
        attendre("Compte rejeté")
    }

    @Test
    fun chercherUnBadge() {
        ecran.toucherTag(ReperesOnglets.onglet("badges"))
        attendreRepere(ReperesEspaceCS.badge("vigik-1"))
        attendre("5 badges")
        ecran.toucherTag(ReperesEspaceCS.BADGES_RECHERCHE)
        ecran.onNodeWithTag(ReperesEspaceCS.BADGES_RECHERCHE).performTextInput("celeste")
        attendreRepere(ReperesEspaceCS.badge("vigik-4"))
        disparu(ReperesEspaceCS.badge("vigik-1"))
        attendre("1 badge sur 5")
    }

    @Test
    fun unCourrielSeDeplie() {
        ecran.toucherTag(ReperesOnglets.onglet("courriels"))
        attendre("Les 50 derniers messages")
        ecran.toucherTag(ReperesEspaceCS.courriel(40))
        attendre("Aucune affaire ne porte le numéro TK-E000066")
        attendre("Camille Essai · envoyé le")
        ecran.toucherTag(ReperesEspaceCS.courriel(41))
        attendreRepere(ReperesEspaceCS.courrielAffaire(41))
        attendre("Affaire #TK-A00012")
    }

    @Test
    fun creerPuisArchiverUneAnnonce() {
        ecran.toucherTag(ReperesOnglets.onglet("annonces-hall"))
        ecran.toucherTag(ReperesEspaceCS.NOUVELLE_TITRE)
        ecran.onNodeWithTag(ReperesEspaceCS.NOUVELLE_TITRE).performTextInput("Ascenseur à l'arrêt")
        ecran.onNodeWithTag(ReperesEspaceCS.NOUVELLE_MESSAGE).performTextInput("Lundi matin.")
        ecran.toucherTag(ReperesEspaceCS.nouvelleFormat("a5"))
        ecran.toucherTag(ReperesEspaceCS.NOUVELLE_CREER)
        attendre("Annonce A5 créée et envoyée à 1 membre du CS")
        assertTrue(residence.recues.any { it.startsWith("POST /api/annonces-hall ") && it.contains("\"a5\"") })

        // Les archives : en cours puis archivées ; le menu « … » range une affiche.
        attendreRepere(ReperesEspaceCS.annonce(2))
        ecran.toucherTag(ReperesEspaceCS.annonceGestes(5))
        ecran.toucherTag(ReperesEspaceCS.ANNONCE_ARCHIVER)
        attendre("Annonce archivée")
        assertTrue(residence.recues.any { it.startsWith("PATCH /api/annonces-hall/5") })
    }

    @Test
    fun leReportingEtSesModes() {
        ecran.toucherTag(ReperesOnglets.onglet("reporting"))
        attendreRepere(ReperesEspaceCS.REPORTING_TOTAL)
        attendre("affaires non fermées")
        attendreRepere(ReperesEspaceCS.reportingAffaire(12))
        attendre("Nouveau message depuis le dernier résumé")
        ecran.toucherTag(ReperesEspaceCS.reportingMode("entretien"))
        attendre("À venir")
        assertTrue(residence.recues.any { it.startsWith("GET /api/suivi-affaires?mode=entretien") })
        ecran.toucherTag(ReperesEspaceCS.reportingMode("renouvellements"))
        attendreRepere(ReperesEspaceCS.RENOUVELLEMENTS_PREAVIS)
        attendre("Préavis en cours")
        attendreRepere(ReperesEspaceCS.renouvellementDiagnostic(1))
    }

    private companion object {
        const val DELAI_MS = 10_000L
    }
}
