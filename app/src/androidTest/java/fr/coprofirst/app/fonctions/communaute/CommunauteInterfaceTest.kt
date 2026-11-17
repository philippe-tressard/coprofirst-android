// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.communaute

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.coprofirst.api.modeles.AnnonceRead
import fr.coprofirst.api.modeles.AvisListe
import fr.coprofirst.api.modeles.AvisRead
import fr.coprofirst.api.modeles.CategorieAnnonce
import fr.coprofirst.api.modeles.IdeeRead
import fr.coprofirst.api.modeles.ReponseCommunauteRead
import fr.coprofirst.api.modeles.SondageRead
import fr.coprofirst.api.modeles.StatutAnnonce
import fr.coprofirst.api.modeles.TypeAnnonce
import fr.coprofirst.app.ThemeCoproFirst
import fr.coprofirst.app.commun.Apparence
import fr.coprofirst.app.commun.CibleSignalement
import fr.coprofirst.app.commun.GestesDeSortie
import fr.coprofirst.app.commun.StockageApparence
import fr.coprofirst.app.commun.attendreTag
import fr.coprofirst.app.commun.attendreTexte
import fr.coprofirst.app.commun.miseenpage.EnFenetreLarge
import fr.coprofirst.app.commun.onglets.ReperesOnglets
import fr.coprofirst.app.commun.toucherTag
import fr.coprofirst.app.navigation.ActionsDeclarees
import fr.coprofirst.app.navigation.BarreDuHaut
import fr.coprofirst.app.navigation.ContexteDeMenu
import fr.coprofirst.app.navigation.DroitsOnglet
import fr.coprofirst.app.navigation.PagesParDefaut
import fr.coprofirst.app.navigation.fermerLaFiche
import fr.coprofirst.app.session.Installation
import fr.coprofirst.app.session.SessionOuverte
import fr.coprofirst.app.session.SessionsParHote
import java.time.OffsetDateTime
import kotlinx.coroutines.delay
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Une Communauté simulée, comme l'API simulée de l'iOS : des contenus inventés (aucune
 * personne réelle), et les droits SERVIS que l'écran doit suivre.
 */
private class CommunauteSimulee(val motif: String? = null, val peutCreer: Boolean = true) : ServiceCommunaute {
    var votes = 0
    val deposes = mutableListOf<String>()
    private var idees = listOf(idee(5, "Un composteur dans la cour", 7, false), idee(3, "Des bacs à fleurs au portail", 12, false))

    override suspend fun moi(): MoiCommunaute {
        delay(100)
        return MoiCommunaute(1, DroitsOnglet.depuis(listOf("résident"), null, "copropriétaire_résident", false), motif)
    }

    override suspend fun sondages() = listOf(
        SondageRead(12, "Quel jour pour la fête des voisins ?", true, LE, "", description = "<p>Trois dates possibles.</p>", nbVotants = 8),
        SondageRead(11, "Repeindre le hall ?", true, LE, "", nbVotants = 30, cloture = true, archivee = true),
    )

    override suspend fun peutCreerUnSondage() = peutCreer
    override suspend fun idees() = idees

    override suspend fun proposerIdee(titre: String, description: String) {
        deposes += titre
    }

    override suspend fun voterIdee(id: Int): String? {
        votes++
        idees = ReglesCommunaute.basculerVote(idees, id)
        return null
    }

    override suspend fun annonces() = listOf(
        AnnonceRead(
            21, "Vélo d'enfant", "<p>Taille 16 pouces, bon état.</p>", TypeAnnonce.VENTE, CategorieAnnonce.DIVERS,
            StatutAnnonce.EN_COURS, 2, LE, "", prix = java.math.BigDecimal("40"), auteurAffiche = "Voisin du 3e",
            auteurEmail = "voisin@residence.test",
        ),
    )

    override suspend fun deposerAnnonce(saisie: SaisieAnnonce) {
        deposes += saisie.titre
    }

    override suspend fun changerStatutAnnonce(id: Int, statut: String) = Unit
    override suspend fun supprimerAnnonce(id: Int) = Unit
    override suspend fun repondre(fil: FilCommunaute, texte: String) {
        deposes += texte
    }

    override suspend fun supprimerReponse(fil: FilCommunaute, reponse: Int) = Unit
    override suspend fun signalerAuConseil(cible: CibleSignalement, motif: String) = Unit
    override suspend fun avis() = AvisListe(listOf(AvisRead(4, 5, "Enfin informé", "<p>Tout est clair.</p>", LE, luDeTous = true)), false, null)
    override suspend fun deposerAvis(saisie: SaisieAvis) = Unit
    override suspend fun corrigerAvis(id: Int, saisie: SaisieAvis) = Unit
    override suspend fun archiverAvis(id: Int, archive: Boolean) = Unit

    private companion object {
        val LE: OffsetDateTime = OffsetDateTime.parse("2026-11-10T09:00:00Z")

        fun idee(id: Int, titre: String, votes: Int, vote: Boolean) = IdeeRead(
            id, titre, "<p>Une idée de la résidence.</p>", "ouverte", LE, "", nbVotes = votes, monVote = vote,
            reponses = listOf(ReponseCommunauteRead(1, "Excellente idée, je veux bien m'en occuper.", LE, "Une voisine")),
            nbReponses = 1,
        )
    }
}

/** La Communauté, comme les tests d'interface de l'iOS (`CommunauteUITests`). */
@RunWith(AndroidJUnit4::class)
class CommunauteInterfaceTest {
    @get:Rule
    val ecran = createComposeRule()

    private fun ouvrir(service: CommunauteSimulee = CommunauteSimulee(), large: Boolean = false): CommunauteSimulee {
        val barre = BarreDuHaut()
        val contexte = ContexteDeMenu(
            page = PagesParDefaut.toutes.first { it.id == "communaute" },
            session = SessionOuverte(Installation("https://residence.test", "Les Glycines"), SessionsParHote()),
            droits = DroitsOnglet.depuis(listOf("résident"), null, "copropriétaire_résident", false),
            sortie = object : GestesDeSortie {
                override fun deconnecter() = Unit
                override fun oublierTout() = Unit
            },
            apparence = Apparence(object : StockageApparence {
                override fun lire(): String? = null
                override fun ecrire(valeur: String) = Unit
            }),
            barre = barre,
        )
        ecran.setContent {
            ThemeCoproFirst {
                Column {
                    Row { ActionsDeclarees(barre) }
                    if (large) EnFenetreLarge { EcranCommunaute(contexte, service) } else EcranCommunaute(contexte, service)
                }
            }
        }
        return service
    }

    private fun el(tag: String): SemanticsNodeInteraction = ecran.onNodeWithTag(tag)

    private fun rubrique(id: String) {
        ecran.toucherTag(ReperesOnglets.onglet(id))
        ecran.waitForIdle()
    }

    @Test
    fun laCommunauteSOuvreEtUneIdeeSeSoutient() {
        val service = ouvrir()
        ecran.attendreTag(ReperesCommunaute.sondage(12))
        el(ReperesCommunaute.SONDAGES_CREATION).assertIsDisplayed()

        rubrique("idees")
        ecran.attendreTag(ReperesCommunaute.voter(5))
        el(ReperesCommunaute.voter(5)).assert(hasContentDescription("Voter pour cette idée", substring = true))
        ecran.toucherTag(ReperesCommunaute.voter(5))
        ecran.waitUntil(DELAI_MS) {
            ecran.onAllNodes(hasTestTag(ReperesCommunaute.voter(5)) and hasContentDescription("Retirer mon vote", substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(1, service.votes)

        ecran.toucherTag(ReperesCommunaute.idee(3))
        ecran.attendreTexte("Excellente idée, je veux bien m'en occuper.")
        ecran.toucherTag(ReperesCommunaute.FERMER)

        rubrique("annonces")
        ecran.attendreTag(ReperesCommunaute.annonce(21))
        ecran.toucherTag(ReperesCommunaute.annonce(21))
        ecran.attendreTag(ReperesCommunaute.ANNONCE_CONTACT)
        ecran.toucherTag(ReperesCommunaute.FERMER)

        rubrique("avis")
        ecran.attendreTag(ReperesCommunaute.avis(4))
        ecran.toucherTag(ReperesCommunaute.CREER)
        ecran.attendreTag(ReperesCommunaute.ENREGISTRER)
        el(ReperesCommunaute.ENREGISTRER).assertIsNotEnabled()
    }

    /**
     * #69, comme `ListeEtFicheUITests` de l'iOS (coprofirst-ios#279) : en fenêtre large, l'annonce
     * s'ouvre À CÔTÉ de la liste, à plat (sans « OK »), sa carte soulignée ; la liste reste ; le
     * geste « retour » referme la fiche.
     */
    @Test
    fun enFenetreLargeLAnnonceSOuvreACoteDeLaListe() {
        ouvrir(large = true)
        rubrique("annonces")
        ecran.attendreTexte("Aucune sélection")
        ecran.toucherTag(ReperesCommunaute.annonce(21))
        ecran.attendreTag(ReperesCommunaute.ANNONCE_CONTACT)
        el(ReperesCommunaute.annonce(21)).assertIsSelected()
        ecran.onAllNodes(hasTestTag(ReperesCommunaute.FERMER)).fetchSemanticsNodes().let { assertEquals("ni « OK » ni voile", 0, it.size) }
        ecran.fermerLaFiche()
        ecran.attendreTexte("Aucune sélection")
        // Plus soulignée : la carte n'est plus dite « sélectionnée » (la propriété n'est posée que choisie).
        assertEquals(0, ecran.onAllNodes(hasTestTag(ReperesCommunaute.annonce(21)) and isSelected()).fetchSemanticsNodes().size)
    }

    /** Sans `peut_creer` servi, l'écran ne dit pas que la création se fait sur le site (#37). */
    @Test
    fun laCreationDUnSondageNeSeDitQuAQuiLePeut() {
        ouvrir(CommunauteSimulee(peutCreer = false))
        ecran.attendreTag(ReperesCommunaute.sondage(12))
        ecran.onAllNodes(hasTestTag(ReperesCommunaute.SONDAGES_CREATION)).fetchSemanticsNodes().let { assertEquals(0, it.size) }
    }

    /** Un compte suspendu lit le motif du serveur, mot pour mot ; aucun onglet ne s'offre. */
    @Test
    fun unCompteSuspenduLitLeMotifDuServeur() {
        ouvrir(CommunauteSimulee(motif = "Votre accès est suspendu jusqu'au 20/11."))
        ecran.attendreTexte("Votre accès est suspendu jusqu'au 20/11.")
        ecran.onAllNodes(hasTestTag(ReperesOnglets.onglet("idees"))).fetchSemanticsNodes().let { assertEquals(0, it.size) }
    }

    /** Une idée se propose : le titre part au serveur, la feuille se ferme. */
    @Test
    fun uneIdeeSePropose() {
        val service = ouvrir()
        rubrique("idees")
        ecran.attendreTag(ReperesCommunaute.CREER)
        ecran.toucherTag(ReperesCommunaute.CREER)
        ecran.attendreTag(ReperesCommunaute.ENREGISTRER)
        ecran.onNode(hasText("Titre")).performTextInput("Un abri à vélos")
        ecran.onNode(hasText("Description")).performTextInput("Couvert, près du local poubelles.")
        ecran.toucherTag(ReperesCommunaute.ENREGISTRER)
        ecran.waitUntil(DELAI_MS) { service.deposes.isNotEmpty() }
        assertEquals(listOf("Un abri à vélos"), service.deposes)
    }

    private companion object {
        const val DELAI_MS = 10_000L
    }
}
