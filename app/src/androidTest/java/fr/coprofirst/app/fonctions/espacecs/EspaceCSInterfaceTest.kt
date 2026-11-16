// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.espacecs

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.coprofirst.app.ThemeCoproFirst
import fr.coprofirst.app.commun.Apparence
import fr.coprofirst.app.commun.GestesDeSortie
import fr.coprofirst.app.commun.StockageApparence
import fr.coprofirst.app.commun.attendreDisparitionTag
import fr.coprofirst.app.commun.attendreTag
import fr.coprofirst.app.commun.attendreTexte
import fr.coprofirst.app.commun.onglets.ReperesOnglets
import fr.coprofirst.app.commun.toucher
import fr.coprofirst.app.commun.toucherTag
import fr.coprofirst.app.commun.validations.ReperesValidations
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

    /** Une pastille de choix : touchée par le geste commun, puis attendue retenue. */
    private fun retenir(repere: String) {
        val retenue = { ecran.onAllNodes(hasTestTag(repere) and isSelected()).fetchSemanticsNodes().isNotEmpty() }
        if (!retenue()) ecran.toucherTag(repere)
        ecran.waitUntil(DELAI_MS, retenue)
    }

    @Test
    fun lesSixOngletsDuSite() {
        listOf("validations", "badges", "reporting", "annonces-hall", "annuaire", "courriels").forEach {
            ecran.attendreTag(ReperesOnglets.onglet(it))
        }
    }

    @Test
    fun approuverUnNouvelArrivantPuisAccepterUneCommande() {
        ecran.attendreTag(ReperesValidations.compte(501))
        ecran.attendreTag(ReperesValidations.COMPTEUR_COMPTES)
        ecran.toucherTag("${ReperesValidations.compte(501)}.approuver")
        ecran.toucherTag(ReperesValidations.NOUVEL_ARRIVANT)
        ecran.attendreTexte("Ancien résident")
        ecran.toucherTag(ReperesValidations.VALIDATION_ENREGISTRER)
        ecran.attendreDisparitionTag(ReperesValidations.compte(501))
        ecran.attendreTexte("Compte activé — 1 lot rattaché.")
        assertTrue(residence.recues.any { it.startsWith("POST /api/admin/utilisateurs/501/accueil-arrivant") })

        ecran.toucherTag("${ReperesValidations.commande(71)}.approuver")
        ecran.toucherTag(ReperesValidations.COMMANDE_CODES)
        ecran.onNodeWithTag(ReperesValidations.COMMANDE_CODES).performTextInput("4521")
        ecran.toucherTag(ReperesValidations.COMMANDE_ACCEPTER)
        ecran.attendreDisparitionTag(ReperesValidations.commande(71))
        ecran.attendreTexte("Commande approuvée")
        assertTrue(residence.recues.any { it.contains("/admin/commandes-acces/71/traiter") && it.contains("\"4521\"") })
    }

    @Test
    fun rejeterUnCompte() {
        ecran.toucherTag("${ReperesValidations.compte(503)}.rejeter")
        ecran.attendreTexte("Rejeter le compte de Alix MODÈLE ?")
        ecran.toucherTag(ReperesValidations.REJET_CONFIRMER)
        ecran.attendreDisparitionTag(ReperesValidations.compte(503))
        ecran.attendreTexte("Compte rejeté")
    }

    @Test
    fun chercherUnBadge() {
        ecran.toucherTag(ReperesOnglets.onglet("badges"))
        ecran.attendreTag(ReperesEspaceCS.badge("vigik-1"))
        ecran.attendreTexte("5 badges")
        ecran.toucherTag(ReperesEspaceCS.BADGES_RECHERCHE)
        ecran.onNodeWithTag(ReperesEspaceCS.BADGES_RECHERCHE).performTextInput("celeste")
        ecran.attendreTag(ReperesEspaceCS.badge("vigik-4"))
        ecran.attendreDisparitionTag(ReperesEspaceCS.badge("vigik-1"))
        ecran.attendreTexte("1 badge sur 5")
    }

    @Test
    fun unCourrielSeDeplie() {
        ecran.toucherTag(ReperesOnglets.onglet("courriels"))
        ecran.attendreTexte("Les 50 derniers messages")
        ecran.toucherTag(ReperesEspaceCS.courriel(40))
        ecran.attendreTexte("Aucune affaire ne porte le numéro TK-E000066")
        ecran.attendreTexte("Camille Essai ·\u00A0envoyé le")
        ecran.toucherTag(ReperesEspaceCS.courriel(41))
        ecran.attendreTag(ReperesEspaceCS.courrielAffaire(41))
        ecran.attendreTexte("Affaire #TK-A00012")
    }

    @Test
    fun creerPuisArchiverUneAnnonce() {
        ecran.toucherTag(ReperesOnglets.onglet("annonces-hall"))
        // Le format et le périmètre AVANT toute frappe (#112). toucher() amène A5 à l'écran
        // à travers la page PUIS la rangée des formats, et attend qu'il soit immobile (#119).
        retenir(ReperesEspaceCS.nouvelleFormat("a5"))
        retenir("${ReperesEspaceCS.NOUVELLE_PERIMETRE}.bat_a")
        // Le lieu que le serveur nomme : il se lit d'un bloc, « Lieu : … » (EtiquetteLieu) —
        // par sa description, pas par son texte (#129).
        ecran.waitUntil(DELAI_MS) {
            ecran.onAllNodes(hasTestTag("${ReperesEspaceCS.NOUVELLE_PERIMETRE}.lieu") and hasContentDescription("Bâtiment A ›", substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
        ecran.toucherTag(ReperesEspaceCS.NOUVELLE_TITRE)
        ecran.onNodeWithTag(ReperesEspaceCS.NOUVELLE_TITRE).performTextInput("Ascenseur à l'arrêt")
        ecran.onNodeWithTag(ReperesEspaceCS.NOUVELLE_MESSAGE).performTextInput("Lundi matin.")
        ecran.toucherTag(ReperesEspaceCS.NOUVELLE_CREER)
        ecran.attendreTexte("Annonce A5 créée et envoyée à 1 membre du CS")
        val creation = synchronized(residence) { residence.recues.toList() }.single { it.startsWith("POST /api/annonces-hall ") }
        assertTrue(creation, creation.contains("\"a5\"") && creation.contains("\"perimetre_cible\":[\"bat_a\"]"))

        // Les archives : en cours puis archivées ; le menu « … » range une affiche.
        ecran.attendreTag(ReperesEspaceCS.annonce(2))
        ecran.toucherTag(ReperesEspaceCS.annonceGestes(5))
        ecran.toucherTag(ReperesEspaceCS.ANNONCE_ARCHIVER)
        ecran.attendreTexte("Annonce archivée")
        assertTrue(residence.recues.any { it.startsWith("PATCH /api/annonces-hall/5") })
    }

    @Test
    fun leReportingEtSesModes() {
        ecran.toucherTag(ReperesOnglets.onglet("reporting"))
        ecran.attendreTag(ReperesEspaceCS.REPORTING_TOTAL)
        ecran.attendreTexte("affaires non fermées")
        ecran.attendreTag(ReperesEspaceCS.reportingAffaire(12))
        ecran.attendreTexte("Nouveau message depuis le dernier résumé")
        ecran.toucherTag(ReperesEspaceCS.reportingMode("entretien"))
        ecran.attendreTexte("À venir")
        assertTrue(residence.recues.any { it.startsWith("GET /api/suivi-affaires?mode=entretien") })
        ecran.toucherTag(ReperesEspaceCS.reportingMode("renouvellements"))
        ecran.attendreTag(ReperesEspaceCS.RENOUVELLEMENTS_PREAVIS)
        ecran.attendreTexte("Préavis en cours")
        ecran.attendreTag(ReperesEspaceCS.renouvellementDiagnostic(1))
    }

    /** Annuaire CS & Syndic : les fiches, l'en-tête du conseil en feuille, puis un interlocuteur monté d'un rang. */
    @Test
    fun lAnnuaireDuConseilEtDuSyndic() {
        ecran.toucherTag(ReperesOnglets.onglet("annuaire"))
        ecran.attendreTag(ReperesEspaceCS.annuaireConseil(0))
        ecran.attendreTexte("Camille EXEMPLE")
        ecran.toucherTag(ReperesEspaceCS.ANNUAIRE_CONSEIL_MODIFIER)
        ecran.attendreTag(ReperesEspaceCS.FEUILLE_ENTETE_CONSEIL)
        ecran.onNodeWithTag("${ReperesEspaceCS.FEUILLE_ENTETE_CONSEIL}.enregistrer").toucher()
        ecran.attendreDisparitionTag(ReperesEspaceCS.FEUILLE_ENTETE_CONSEIL)
        ecran.attendreTexte("Conseil Syndical enregistré")
        assertTrue(residence.recues.any { it.startsWith("PUT /api/admin/annuaire/cs") && it.contains("\"ag_annee\":2026") })

        ecran.toucherTag("${ReperesEspaceCS.annuaireSyndic(1)}.gestes")
        ecran.toucherTag("${ReperesEspaceCS.annuaireSyndic(1)}.monter")
        ecran.attendreTexte("Syndic enregistré")
        assertTrue(residence.recues.any { it.startsWith("PUT /api/admin/annuaire/syndic") && it.indexOf("Sacha") < it.indexOf("Dominique") })
    }

    private companion object {
        const val DELAI_MS = 10_000L
    }
}
