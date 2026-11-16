// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.espacecs

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.coprofirst.app.ThemeCoproFirst
import fr.coprofirst.app.commun.Apparence
import fr.coprofirst.app.commun.GestesDeSortie
import fr.coprofirst.app.commun.StockageApparence
import fr.coprofirst.app.commun.onglets.ReperesOnglets
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

    /** Un élément hors de toute liste (une confirmation, une feuille). */
    private fun toucher(repere: String) {
        attendreRepere(repere)
        ecran.onNodeWithTag(repere).performClick()
    }

    /** Un élément de la page qui défile : il s'amène à l'écran d'abord. */
    private fun atteindre(repere: String) {
        attendreRepere(repere)
        ecran.onNodeWithTag(repere).performScrollTo().performClick()
    }

    /** Une pastille de choix : touchée jusqu'à ce qu'elle soit retenue (la page peut bouger sous le doigt). */
    private fun retenir(repere: String) {
        val retenue = { ecran.onAllNodes(hasTestTag(repere) and isSelected()).fetchSemanticsNodes().isNotEmpty() }
        repeat(3) {
            if (retenue()) return
            attendreRepere(repere)
            ecran.onNodeWithTag(repere).performScrollTo().assertIsDisplayed()
            attendreImmobile(repere)
            ecran.onNodeWithTag(repere).performClick()
            ecran.waitForIdle()
        }
        if (retenue()) return
        // Le toucher n'a pas porté (CI de #112, A5) : l'action de la pastille, puis le constat
        // de ce qui a fait obstacle, dans le message.
        val noeud = ecran.onNodeWithTag(repere).fetchSemanticsNode()
        ecran.onNodeWithTag(repere).performSemanticsAction(SemanticsActions.OnClick)
        ecran.waitUntil(DELAI_MS, retenue)
        println("retenir($repere) : retenue par son action seule ; bornes ${noeud.boundsInRoot}, racine ${noeud.layoutInfo.coordinates.size}")
    }

    /** Attend que le nœud ne bouge plus (deux lectures de sa position identiques), 3 s au plus. */
    private fun attendreImmobile(repere: String) {
        var avant = ecran.onNodeWithTag(repere).fetchSemanticsNode().boundsInRoot
        repeat(30) {
            Thread.sleep(100)
            val apres = ecran.onNodeWithTag(repere).fetchSemanticsNode().boundsInRoot
            if (apres == avant) return
            avant = apres
        }
    }

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
        atteindre("${ReperesEspaceCS.compte(501)}.approuver")
        toucher(ReperesEspaceCS.NOUVEL_ARRIVANT)
        attendre("Ancien résident")
        ecran.onNodeWithTag(ReperesEspaceCS.VALIDATION_ENREGISTRER).performScrollTo().performClick()
        disparu(ReperesEspaceCS.compte(501))
        attendre("Compte activé — 1 lot rattaché.")
        assertTrue(residence.recues.any { it.startsWith("POST /api/admin/utilisateurs/501/accueil-arrivant") })

        atteindre("${ReperesEspaceCS.commande(71)}.approuver")
        toucher(ReperesEspaceCS.COMMANDE_CODES)
        ecran.onNodeWithTag(ReperesEspaceCS.COMMANDE_CODES).performTextInput("4521")
        toucher(ReperesEspaceCS.COMMANDE_ACCEPTER)
        disparu(ReperesEspaceCS.commande(71))
        attendre("Commande approuvée")
        assertTrue(residence.recues.any { it.contains("/admin/commandes-acces/71/traiter") && it.contains("\"4521\"") })
    }

    @Test
    fun rejeterUnCompte() {
        atteindre("${ReperesEspaceCS.compte(503)}.rejeter")
        attendre("Rejeter le compte de Alix MODÈLE ?")
        toucher(ReperesEspaceCS.REJET_CONFIRMER)
        disparu(ReperesEspaceCS.compte(503))
        attendre("Compte rejeté")
    }

    @Test
    fun chercherUnBadge() {
        atteindre(ReperesOnglets.onglet("badges"))
        attendreRepere(ReperesEspaceCS.badge("vigik-1"))
        attendre("5 badges")
        toucher(ReperesEspaceCS.BADGES_RECHERCHE)
        ecran.onNodeWithTag(ReperesEspaceCS.BADGES_RECHERCHE).performTextInput("celeste")
        attendreRepere(ReperesEspaceCS.badge("vigik-4"))
        disparu(ReperesEspaceCS.badge("vigik-1"))
        attendre("1 badge sur 5")
    }

    @Test
    fun unCourrielSeDeplie() {
        atteindre(ReperesOnglets.onglet("courriels"))
        attendre("Les 50 derniers messages")
        atteindre(ReperesEspaceCS.courriel(40))
        attendre("Aucune affaire ne porte le numéro TK-E000066")
        attendre("Camille Essai · envoyé le")
        atteindre(ReperesEspaceCS.courriel(41))
        attendreRepere(ReperesEspaceCS.courrielAffaire(41))
        attendre("Affaire #TK-A00012")
    }

    @Test
    fun creerPuisArchiverUneAnnonce() {
        atteindre(ReperesOnglets.onglet("annonces-hall"))
        // Le format et le périmètre AVANT toute frappe : le clavier, ouvert par la frappe, couvre
        // le bas de la page (la sémantique l'ignore) et le toucher tombait sur lui (CI de #112).
        // Le lieu que le serveur nomme arrive après le choix et déplace la page : chaque
        // pastille est touchée immobile, jusqu'à être retenue.
        retenir(ReperesEspaceCS.nouvelleFormat("a5"))
        retenir("${ReperesEspaceCS.NOUVELLE_PERIMETRE}.bat_a")
        attendre("Bâtiment A ›")
        toucher(ReperesEspaceCS.NOUVELLE_TITRE)
        ecran.onNodeWithTag(ReperesEspaceCS.NOUVELLE_TITRE).performTextInput("Ascenseur à l'arrêt")
        ecran.onNodeWithTag(ReperesEspaceCS.NOUVELLE_MESSAGE).performTextInput("Lundi matin.")
        Espresso.closeSoftKeyboard()
        atteindre(ReperesEspaceCS.NOUVELLE_CREER)
        attendre("Annonce A5 créée et envoyée à 1 membre du CS")
        val creation = synchronized(residence) { residence.recues.toList() }.single { it.startsWith("POST /api/annonces-hall ") }
        assertTrue(creation, creation.contains("\"a5\"") && creation.contains("\"perimetre_cible\":[\"bat_a\"]"))

        // Les archives : en cours puis archivées ; le menu « … » range une affiche.
        attendreRepere(ReperesEspaceCS.annonce(2))
        atteindre(ReperesEspaceCS.annonceGestes(5))
        toucher(ReperesEspaceCS.ANNONCE_ARCHIVER)
        attendre("Annonce archivée")
        assertTrue(residence.recues.any { it.startsWith("PATCH /api/annonces-hall/5") })
    }

    @Test
    fun leReportingEtSesModes() {
        atteindre(ReperesOnglets.onglet("reporting"))
        attendreRepere(ReperesEspaceCS.REPORTING_TOTAL)
        attendre("affaires non fermées")
        attendreRepere(ReperesEspaceCS.reportingAffaire(12))
        attendre("Nouveau message depuis le dernier résumé")
        atteindre(ReperesEspaceCS.reportingMode("entretien"))
        attendre("À venir")
        assertTrue(residence.recues.any { it.startsWith("GET /api/suivi-affaires?mode=entretien") })
        atteindre(ReperesEspaceCS.reportingMode("renouvellements"))
        attendreRepere(ReperesEspaceCS.RENOUVELLEMENTS_PREAVIS)
        attendre("Préavis en cours")
        attendreRepere(ReperesEspaceCS.renouvellementDiagnostic(1))
    }

    private companion object {
        const val DELAI_MS = 10_000L
    }
}
