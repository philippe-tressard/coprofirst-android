// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.prestataires

import androidx.compose.ui.test.hasContentDescription
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * « Prestataires » (#19) à l'écran, comme l'iOS (`PrestatairesUITests`) : les trois onglets
 * s'ouvrent, la recherche filtre, une fiche et un contrat se déplient — contre les réponses
 * simulées, servies en HTTPS à l'app par son vrai client. Repères : ceux de l'iOS.
 */
@RunWith(AndroidJUnit4::class)
class EcranPrestatairesInterfaceTest {
    @get:Rule
    val ecran = createComposeRule()
    private val simules = PrestatairesSimules()
    private val certificat = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
    private val serveur = MockWebServer().apply {
        useHttps(HandshakeCertificates.Builder().heldCertificate(certificat).build().sslSocketFactory())
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val chemin = request.url.encodedPath + (request.url.encodedQuery?.let { "?$it" } ?: "")
                val (code, corps) = simules.repondre(request.method, chemin)
                return MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(corps).build()
            }
        }
        start()
    }
    private val conseil = DroitsOnglet.depuis(listOf("conseil_syndical"), null, "copropriétaire_résident", estModerateur = true)

    @Before
    fun ouvrir() {
        val confiance = HandshakeCertificates.Builder().addTrustedCertificate(certificat.certificate).build()
        val client = ClientHttp.partage.newBuilder().sslSocketFactory(confiance.sslSocketFactory(), confiance.trustManager).build()
        val session = SessionOuverte(Installation("https://residence.test", "Les Glycines"), SessionsParHote(client)) {
            serveur.url("/api").toString()
        }
        val contexte = ContexteDeMenu(
            page = PagesParDefaut.toutes.first { it.id == "prestataires" },
            session = session,
            droits = conseil,
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
        ecran.setContent { ThemeCoproFirst { EcranPrestataires(contexte, ServicePrestatairesApi(session)) } }
    }

    @After
    fun fermer() = serveur.close()

    /** Un texte à l'écran — ou dans la description d'un élément qui la remplace (`LigneDocument`). */
    private fun attendre(texte: String) = ecran.waitUntil(DELAI_MS) {
        ecran.onAllNodes(hasText(texte, substring = true) or hasContentDescription(texte, substring = true))
            .fetchSemanticsNodes().isNotEmpty()
    }

    private fun attendreRepere(repere: String) =
        ecran.waitUntil(DELAI_MS) { ecran.onAllNodes(hasTestTag(repere)).fetchSemanticsNodes().isNotEmpty() }

    private fun absent(repere: String) =
        ecran.waitUntil(DELAI_MS) { ecran.onAllNodes(hasTestTag(repere)).fetchSemanticsNodes().isEmpty() }

    @Test
    fun lesTroisOngletsSOuvrent() {
        attendreRepere(ReperesPrestataires.prestataire(1))
        ecran.toucherTag(ReperesPrestataires.prestataire(1))
        attendre("📮 12 rue de l'Exemple")

        ecran.onNodeWithTag(ReperesPrestataires.RECHERCHE).performTextInput("incendie controles")
        attendreRepere(ReperesPrestataires.prestataire(2))
        absent(ReperesPrestataires.prestataire(1))

        ecran.toucherTag("onglet.contrats")
        ecran.toucherTag(ReperesPrestataires.contrat(1))
        attendre("Le contrat".uppercase())
        attendre("↺ Mensuel")
        attendre("contrat-ascenseurs")
        // 🔁 Le remplacement servi, sur les deux cartes.
        attendreRepere(ReperesPrestataires.remplacement(2, 0))
        attendre("🔁 Remplacé par Protection Incendie Exemple")
        attendre("🔁 Remplace Contrôles Sécurité Exemple (résilié au 30/11/2026)")

        ecran.toucherTag("onglet.consommations")
        // « 47 047 » : l'espace insécable de `FormatsDuSite.nombre`.
        attendre("Index 47")
        attendre("🔧 Eaux de la Ville")
    }

    /** Le récapitulatif servi : l'exercice le plus récent ouvert, un badge qui déplie l'avis. */
    @Test
    fun lesAvisDUnPrestataireSeDeplient() {
        ecran.toucherTag(ReperesPrestataires.prestataire(1))
        attendreRepere(ReperesAvis.exercice(2026))
        attendre("Avis du conseil syndical (3)".uppercase())
        attendre("non notée")
        ecran.toucherTag(ReperesAvis.avis(11))
        attendreRepere(ReperesAvis.detail(11))
        attendre("délai respecté")
        // L'exercice 2025 est replié : son avis ne se voit pas.
        absent(ReperesAvis.avis(9))
        ecran.toucherTag(ReperesAvis.exercice(2025))
        attendreRepere(ReperesAvis.avis(9))
    }

    @Test
    fun lesArchivesSeDeplient() {
        attendreRepere(ReperesPrestataires.ARCHIVES)
        ecran.toucherTag(ReperesPrestataires.ARCHIVES)
        attendreRepere(ReperesPrestataires.prestataire(9))
    }

    private companion object {
        const val DELAI_MS = 10_000L
    }
}
