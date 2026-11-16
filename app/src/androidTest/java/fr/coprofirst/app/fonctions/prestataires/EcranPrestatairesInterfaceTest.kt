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
import fr.coprofirst.app.commun.attendreDisparitionTag
import fr.coprofirst.app.commun.attendreTag
import fr.coprofirst.app.commun.attendreTexteOuDescription
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

    @Test
    fun lesTroisOngletsSOuvrent() {
        ecran.attendreTag(ReperesPrestataires.prestataire(1))
        ecran.toucherTag(ReperesPrestataires.prestataire(1))
        ecran.attendreTexteOuDescription("📮 12 rue de l'Exemple")

        ecran.onNodeWithTag(ReperesPrestataires.RECHERCHE).performTextInput("incendie controles")
        ecran.attendreTag(ReperesPrestataires.prestataire(2))
        ecran.attendreDisparitionTag(ReperesPrestataires.prestataire(1))

        ecran.toucherTag("onglet.contrats")
        ecran.toucherTag(ReperesPrestataires.contrat(1))
        ecran.attendreTexteOuDescription("Le contrat".uppercase())
        ecran.attendreTexteOuDescription("↺ Mensuel")
        ecran.attendreTexteOuDescription("contrat-ascenseurs")
        // 🔁 Le remplacement servi, sur les deux cartes.
        ecran.attendreTag(ReperesPrestataires.remplacement(2, 0))
        ecran.attendreTexteOuDescription("🔁 Remplacé par Protection Incendie Exemple")
        ecran.attendreTexteOuDescription("🔁 Remplace Contrôles Sécurité Exemple (résilié au 30/11/2026)")

        ecran.toucherTag("onglet.consommations")
        // « 47 047 » : l'espace insécable de `FormatsDuSite.nombre`.
        ecran.attendreTexteOuDescription("Index 47")
        ecran.attendreTexteOuDescription("🔧 Eaux de la Ville")
    }

    /** Le récapitulatif servi : l'exercice le plus récent ouvert, un badge qui déplie l'avis. */
    @Test
    fun lesAvisDUnPrestataireSeDeplient() {
        ecran.toucherTag(ReperesPrestataires.prestataire(1))
        ecran.attendreTag(ReperesAvis.exercice(2026))
        ecran.attendreTexteOuDescription("Avis du conseil syndical (3)".uppercase())
        ecran.attendreTexteOuDescription("non notée")
        ecran.toucherTag(ReperesAvis.avis(11))
        ecran.attendreTag(ReperesAvis.detail(11))
        ecran.attendreTexteOuDescription("délai respecté")
        // L'exercice 2025 est replié : son avis ne se voit pas.
        ecran.attendreDisparitionTag(ReperesAvis.avis(9))
        ecran.toucherTag(ReperesAvis.exercice(2025))
        ecran.attendreTag(ReperesAvis.avis(9))
    }

    @Test
    fun lesArchivesSeDeplient() {
        ecran.attendreTag(ReperesPrestataires.ARCHIVES)
        ecran.toucherTag(ReperesPrestataires.ARCHIVES)
        ecran.attendreTag(ReperesPrestataires.prestataire(9))
    }

    private companion object {
        const val DELAI_MS = 10_000L
    }
}
