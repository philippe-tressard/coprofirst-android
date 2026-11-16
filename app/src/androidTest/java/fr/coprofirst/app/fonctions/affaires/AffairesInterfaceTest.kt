// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.affaires

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.coprofirst.app.ThemeCoproFirst
import fr.coprofirst.app.commun.AvisDuGeste
import fr.coprofirst.app.commun.AvisEphemere
import fr.coprofirst.app.commun.LocalSaisieAffaire
import fr.coprofirst.app.commun.amenerTag
import fr.coprofirst.app.commun.apercu.SaisieLecture
import fr.coprofirst.app.commun.attendreDisparitionTag
import fr.coprofirst.app.commun.attendreTag
import fr.coprofirst.app.commun.attendreTexte
import fr.coprofirst.app.commun.formats.FormatsDuSite
import fr.coprofirst.app.commun.lecture.Lecture
import fr.coprofirst.app.commun.perimetres.Perimetre
import fr.coprofirst.app.commun.toucher
import fr.coprofirst.app.navigation.ActionsDeclarees
import fr.coprofirst.app.navigation.BarreDuHaut
import fr.coprofirst.app.navigation.DroitsOnglet
import fr.coprofirst.app.session.ErreurService
import fr.coprofirst.app.session.Installation
import fr.coprofirst.app.session.SessionOuverte
import fr.coprofirst.app.session.SessionsParHote
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.delay
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Une résidence simulée, comme l'API simulée de l'iOS : des affaires inventées (aucune
 * personne), le conseil syndical qui les lit — il déplace au Kanban et archive.
 */
private class AffairesSimulees : ServiceAffaires {
    /** Les saisies dont « Qui la lira » a été demandé (#179). */
    val lectures = mutableListOf<SaisieLecture>()
    override suspend fun lecture(saisie: SaisieLecture): Lecture {
        synchronized(lectures) { lectures += saisie }
        return Lecture(
            "coproprietaires", listOf("key-round"), "Copropriétaires", "Copropriétaires (occupants et bailleurs)", false,
            "Lue par les copropriétaires, dans toute la copropriété.", "Pas les locataires.", false,
        )
    }
    val deplacements = mutableListOf<Pair<Int, String>>()
    val archivees = mutableListOf<Int>()
    private val maintenant = Instant.now()
    var affaires = listOf(
        affaire(1, "A-0001", "Fuite dans le local vélos", "ouvert", "Ouvert", "cs", urgente = true),
        affaire(2, "A-0002", "Ascenseur du bâtiment B en panne", "en_cours", "Chez le syndic", "syndic"),
        affaire(3, "A-0003", "Coupure d'eau jeudi", "publie", "Publiée", null, categorie = "actualite", natures = listOf("actualite")),
        affaire(4, "A-0004", "Porte du hall réparée", "résolu", "Résolu", null, archivee = true, jours = 400),
        affaire(5, "A-0005", "Éclairage du parking", "résolu", "Résolu", null, archivee = true, jours = 20),
    )

    private fun affaire(
        id: Int, numero: String, titre: String, statut: String, libelle: String, colonne: String?,
        urgente: Boolean = false, archivee: Boolean = false, categorie: String = "panne",
        natures: List<String> = listOf("activite"), jours: Long = 2,
    ) = Affaire(
        id = id, numero = numero, titre = titre, apercu = "<p>Le détail de « $titre ».</p>",
        creeLe = maintenant.minus(jours, ChronoUnit.DAYS), statut = statut, statutLibelle = libelle, close = archivee,
        categorie = categorie, categorieLibelle = if (categorie == "actualite") "Actualité" else "Panne", urgente = urgente,
        archivee = archivee, suiviKanban = colonne != null, natures = natures, colonne = colonne,
        peutDeplacer = true, peutArchiver = true,
    )

    override suspend fun affaires(): List<Affaire> {
        delay(100)
        return affaires
    }
    override suspend fun referentiels() = referentielsLus

    private val referentielsLus = ReferentielsAffaires(
        listOf(ColonneKanban("cs", "CS (en cours)", "CS"), ColonneKanban("syndic", "Syndic (en cours)", "Syndic"), ColonneKanban("termine", "Terminé", "Terminé")),
        listOf(
            ReferentielsAffaires.Statut("ouvert", "Ouvert", false, "cs", true),
            ReferentielsAffaires.Statut("en_cours", "Chez le syndic", false, "syndic", true),
            ReferentielsAffaires.Statut("résolu", "Résolu", true, "termine", true),
            ReferentielsAffaires.Statut("publie", "Publiée", false, null, false),
        ),
        listOf(
            ReferentielsAffaires.Categorie(
                "panne", "Panne", "🛠️", "Ascenseur, chauffage, éclairage", carnet = true, permise = true,
                sectionsEssentielles = listOf("titre", "description", "perimetre"),
            ),
            ReferentielsAffaires.Categorie("question", "Question", "❓", "Une question au conseil", permise = true, sectionsEssentielles = listOf("titre", "description")),
        ),
    )
    override suspend fun rechercher(terme: String) = affaires.filter { terme.lowercase() in it.titre.lowercase() }.map { it.id }
    override suspend fun offrirMeConcerne() = true
    override suspend fun candidatesALaFusion(id: Int) = 2
    override suspend fun deplacer(id: Int, statut: String) {
        deplacements += id to statut
    }
    override suspend fun archiver(id: Int): Affaire {
        archivees += id
        return affaires.first { it.id == id }.copy(archivee = true)
    }
    val creees = mutableListOf<SaisieAffaire>()
    val reponses = mutableListOf<Pair<Int, String>>()
    val suites = mutableListOf<Pair<Int, SaisieSuite>>()
    override suspend fun perimetres() = listOf(
        Perimetre("copro", "Toute la copropriété", parDefaut = true), Perimetre("bat-b", "Bâtiment B", 1, parent = "copro"),
    )
    override suspend fun lieu(codes: List<String>) = if (codes == listOf("bat-b")) "Bâtiment B" else "Toute la copropriété"
    override suspend fun creer(saisie: SaisieAffaire): Affaire {
        creees += saisie
        return affaires.first().copy(id = 9, numero = "A-0009", titre = saisie.titre.trim(), urgente = false, colonne = null, suiviKanban = false)
    }
    override suspend fun repondre(id: Int, texte: String) {
        reponses += id to texte
    }
    override suspend fun ajouterSuite(id: Int, suite: SaisieSuite) {
        suites += id to suite
    }
    override suspend fun regler(id: Int, option: OptionAffaire, active: Boolean) = Unit
    override suspend fun prestatairesDeLaResidence() = listOf(ASCENSEURS, PLOMBERIE)
    val editions = mutableListOf<EditionAffaire>()
    override suspend fun enregistrer(id: Int, edition: EditionAffaire) {
        editions += edition
    }
    val corrections = mutableListOf<CorrectionSuite>()
    val supprimees = mutableListOf<Int>()
    override suspend fun corrigerSuite(id: Int, suite: Int, correction: CorrectionSuite): IssueCorrection? {
        corrections += correction
        return IssueCorrection("L'affaire a avancé depuis : elle garde son état.", "en_cours", "Chez le syndic")
    }
    override suspend fun supprimerSuite(id: Int, suite: Int) {
        supprimees += suite
    }
    override suspend fun affaire(id: Int): DetailAffaire {
        val a = affaires.firstOrNull { it.id == id } ?: throw ErreurService.Introuvable
        return DetailAffaire(
            id = a.id, numero = a.numero, titre = a.titre, description = a.apercu, categorie = a.categorie, statut = a.statut,
            statutLibelle = a.statutLibelle, creeLe = a.creeLe, auteur = "Le conseil syndical",
            suites = listOf(
                SuiteAffaire(
                    31, "etat", a.creeLe.plusSeconds(3600), "Le syndic", "<p>Le syndic est prévenu.</p>", "ouvert", "en_cours",
                    peutModifier = true, peutSupprimer = true,
                ),
            ),
            messages = listOf(MessageAffaire(41, "<p>Merci pour le suivi.</p>", a.creeLe.plusSeconds(7200), true, null, null, false)),
            referentiels = referentielsLus,
            droits = DroitsServisAffaire(repondre = true, changerEtat = true, modifier = true, corriger = true, changerPrestataires = true),
            perimetres = listOf("copro"),
            prestataires = listOf(ASCENSEURS),
        )
    }

    companion object {
        val ASCENSEURS = PrestataireAffaire(3, "Ascenseurs du Parc")
        val PLOMBERIE = PrestataireAffaire(5, "Plomberie Martin")
    }
}

/** « Mes affaires », comme les tests d'interface de l'iOS (`AffairesUITests`). */
@RunWith(AndroidJUnit4::class)
class AffairesInterfaceTest {
    @get:Rule
    val ecran = createComposeRule()

    /** Les fiches que les cartes ont demandées à la porte. */
    private val fiches = mutableListOf<Int>()
    private val session = SessionOuverte(Installation("https://residence.exemple.test", "Les Glycines"), SessionsParHote())

    private fun ouvrir(droits: DroitsOnglet = DroitsOnglet(cs = true, proprioOuCS = true), saisie: String? = null): AffairesSimulees {
        val service = AffairesSimulees()
        val barre = BarreDuHaut()
        ecran.setContent {
            ThemeCoproFirst {
                CompositionLocalProvider(LocalSaisieAffaire provides saisie) {
                    Column {
                        Row { ActionsDeclarees(barre) }
                        EcranAffaires(service, barre, droits, { "https://residence.exemple.test/tickets/$it" }) { fiches += it }
                    }
                }
            }
        }
        return service
    }

    /** La fiche, telle que la porte la pousse. */
    private fun ouvrirFiche(id: Int, conseil: Boolean = false): AffairesSimulees {
        val service = AffairesSimulees()
        val avis = AvisDuGeste()
        val fiche = ContexteFiche(service, session, conseil, avis, null) { "https://residence.exemple.test/tickets/$it" }
        ecran.setContent { ThemeCoproFirst { AvisEphemere(avis) { FicheAffaire(id, fiche) } } }
        return service
    }

    private fun el(tag: String): SemanticsNodeInteraction = ecran.onNodeWithTag(tag, useUnmergedTree = true)

    /** La liste montre les affaires actives ; le filtre « Suivi » retient un état, son compte sur la pastille. */
    @Test
    fun laListeSeFiltreParSuivi() {
        ouvrir()
        ecran.attendreTag(ReperesAffaires.carte("A-0001"), useUnmergedTree = true)
        el(ReperesAffaires.carte("A-0002")).assertIsDisplayed()
        ecran.attendreDisparitionTag(ReperesAffaires.carte("A-0004"), useUnmergedTree = true)
        el(ReperesAffaires.suivi("ouvert")).toucher()
        ecran.attendreDisparitionTag(ReperesAffaires.carte("A-0002"), useUnmergedTree = true)
        el(ReperesAffaires.carte("A-0001")).assertIsDisplayed()
        el(ReperesAffaires.suivi(null)).toucher()
        ecran.attendreTag(ReperesAffaires.carte("A-0002"), useUnmergedTree = true)
    }

    /** La recherche interroge le serveur et propose les archives qu'elle a trouvées. */
    @Test
    fun laRechercheProposeLesArchives() {
        ouvrir()
        ecran.attendreTag(ReperesAffaires.carte("A-0001"), useUnmergedTree = true)
        el(ReperesAffaires.RECHERCHE).performTextInput("porte")
        ecran.attendreTag(ReperesAffaires.INCLURE_ARCHIVES, useUnmergedTree = true)
        ecran.attendreTexte("Aucune affaire trouvée", useUnmergedTree = true)
    }

    /** L'appui long offre « Archiver » (`peut_archiver` servi) : la carte part aux Archives. */
    @Test
    fun uneAffaireSArchive() {
        val service = ouvrir()
        ecran.attendreTag(ReperesAffaires.carte("A-0002"), useUnmergedTree = true)
        el(ReperesAffaires.carte("A-0002")).performTouchInput { longClick() }
        ecran.attendreTag(ReperesAffaires.archiver("A-0002"), useUnmergedTree = true)
        el(ReperesAffaires.archiver("A-0002")).toucher()
        ecran.attendreDisparitionTag(ReperesAffaires.carte("A-0002"), useUnmergedTree = true)
        assertEquals(listOf(2), service.archivees)
    }

    /** Au Kanban, « Déplacer vers… » pose l'état servi ; clore demande d'abord, à cause des liées. */
    @Test
    fun uneCarteSeDeplaceAuKanban() {
        val service = ouvrir()
        ecran.attendreTag("onglet.kanban", useUnmergedTree = true)
        el("onglet.kanban").toucher()
        ecran.attendreTag(ReperesAffaires.carteKanban("A-0001"), useUnmergedTree = true)
        ecran.attendreTexte("2 affaires suivies", useUnmergedTree = true)
        el(ReperesAffaires.carteKanban("A-0001")).performTouchInput { longClick() }
        ecran.attendreTag(ReperesAffaires.deplacer("A-0001", "syndic"), useUnmergedTree = true)
        el(ReperesAffaires.deplacer("A-0001", "syndic")).toucher()
        ecran.waitUntil(DELAI_MS) { service.deplacements == listOf(1 to "en_cours") }
        el(ReperesAffaires.carteKanban("A-0002")).performTouchInput { longClick() }
        ecran.attendreTag(ReperesAffaires.deplacer("A-0002", "termine"), useUnmergedTree = true)
        el(ReperesAffaires.deplacer("A-0002", "termine")).toucher()
        ecran.attendreTag(ReperesAffaires.CLORE_SANS_FUSION, useUnmergedTree = true)
        el(ReperesAffaires.CLORE_SANS_FUSION).toucher()
        ecran.waitUntil(DELAI_MS) { service.deplacements.lastOrNull() == (2 to "résolu") }
    }

    /** Le Kanban est réservé : un locataire ne le voit pas. */
    @Test
    fun unLocataireNAPasDeKanban() {
        ouvrir(DroitsOnglet(locataire = true))
        ecran.attendreTag("onglet.archives", useUnmergedTree = true)
        ecran.attendreDisparitionTag("onglet.kanban", useUnmergedTree = true)
    }

    /** Les Archives se rangent par année : la plus récente ouverte, les autres repliées. */
    @Test
    fun lesArchivesSeReplientParAnnee() {
        ouvrir()
        ecran.attendreTag("onglet.archives", useUnmergedTree = true)
        el("onglet.archives").toucher()
        ecran.attendreTag(ReperesAffaires.archive("A-0005"), useUnmergedTree = true)
        ecran.attendreDisparitionTag(ReperesAffaires.archive("A-0004"), useUnmergedTree = true)
        // 400 jours plus tôt : toujours une autre année que 20 jours plus tôt.
        val annee = Instant.now().minus(400, ChronoUnit.DAYS).atZone(FormatsDuSite.PARIS).year
        el(ReperesAffaires.annee(annee)).toucher()
        ecran.attendreTag(ReperesAffaires.archive("A-0004"), useUnmergedTree = true)
    }

    /** Une carte ouvre sa fiche par la porte. */
    @Test
    fun uneCarteOuvreSaFicheParLaPorte() {
        ouvrir()
        ecran.attendreTag(ReperesAffaires.carte("A-0001"), useUnmergedTree = true)
        el(ReperesAffaires.carte("A-0001")).toucher()
        ecran.waitUntil(DELAI_MS) { fiches == listOf(1) }
    }

    /** La fiche : le fil, la suite, les messages ; un toucher ouvre une entrée. */
    @Test
    fun laFicheMontreLeFilEtLesMessages() {
        ouvrirFiche(1)
        ecran.attendreTag(ReperesAffaires.etape(EtapeFil.Cle.Suite), useUnmergedTree = true)
        ecran.attendreTexte("Ouvert → Chez le syndic", useUnmergedTree = true)
        ecran.attendreTag(ReperesAffaires.message(41), useUnmergedTree = true)
        el(ReperesAffaires.element("suite.31")).toucher()
        ecran.attendreTexte("Le syndic est prévenu.", useUnmergedTree = true)
    }

    /** « Nouvelle affaire » : catégorie, titre, description, puis Envoyer — l'affaire paraît en tête. */
    @Test
    fun uneNouvelleAffaireSEnvoie() {
        val service = ouvrir()
        ecran.attendreTag(ReperesNouvelle.NOUVELLE, useUnmergedTree = true)
        el(ReperesNouvelle.NOUVELLE).toucher()
        ecran.attendreTag(ReperesChamps.NOUVELLE.categorie("panne"), useUnmergedTree = true)
        el(ReperesNouvelle.DEPUIS_LE_SITE).assertExists()
        el(ReperesChamps.NOUVELLE.categorie("panne")).toucher()
        el(ReperesChamps.NOUVELLE.titre).performTextInput("Interphone muet")
        el(ReperesChamps.NOUVELLE.description).performTextInput("Le bouton du 3e ne sonne plus.")
        el(ReperesNouvelle.ENVOYER).toucher()
        ecran.attendreTag(ReperesAffaires.carte("A-0009"), useUnmergedTree = true)
        assertEquals("copro", service.creees.single().perimetre)
        assertEquals("panne", service.creees.single().categorie)
    }

    /**
     * #178 : en saisie Essentielle, ce qui n'est pas essentiel pour la catégorie se range dans
     * « Plus de détails », plié ; la phrase le dit ; ce qui part ne change pas.
     */
    @Test
    fun enSaisieEssentielleCeQuiNEstPasEssentielSeRange() {
        val service = ouvrir(saisie = "essentielle")
        ecran.attendreTag(ReperesNouvelle.NOUVELLE, useUnmergedTree = true)
        el(ReperesNouvelle.NOUVELLE).toucher()
        ecran.attendreTag(ReperesChamps.NOUVELLE.categorie("panne"), useUnmergedTree = true)
        el(ReperesChamps.NOUVELLE.categorie("panne")).toucher()
        el(ReperesChamps.NOUVELLE.modeSaisie).assertExists()
        ecran.onNodeWithTag(ReperesChamps.NOUVELLE.plusDeDetails).assert(hasText("Plus de détails (1 section)", substring = true))
        el(ReperesChamps.NOUVELLE.urgente).assertDoesNotExist()
        el(ReperesChamps.NOUVELLE.lieu).assertExists()
        el(ReperesChamps.NOUVELLE.plusDeDetails).toucher()
        ecran.attendreTag(ReperesChamps.NOUVELLE.urgente, useUnmergedTree = true)
        el(ReperesChamps.NOUVELLE.categorie("question")).toucher()
        ecran.onNodeWithTag(ReperesChamps.NOUVELLE.plusDeDetails).assert(hasText("Plus de détails (2 sections)", substring = true))
        el(ReperesChamps.NOUVELLE.titre).performTextInput("Horaires du local vélos")
        el(ReperesChamps.NOUVELLE.description).performTextInput("Le local ferme-t-il la nuit ?")
        el(ReperesNouvelle.ENVOYER).toucher()
        ecran.attendreTag(ReperesAffaires.carte("A-0009"), useUnmergedTree = true)
        assertEquals("le lieu rangé part à son défaut", "copro", service.creees.single().perimetre)
    }

    /** #178 : en saisie Complète, tout reste à son rang, et la phrase dit la saisie. */
    @Test
    fun enSaisieCompleteToutResteASonRang() {
        ouvrir(saisie = "complete")
        ecran.attendreTag(ReperesNouvelle.NOUVELLE, useUnmergedTree = true)
        el(ReperesNouvelle.NOUVELLE).toucher()
        ecran.attendreTag(ReperesChamps.NOUVELLE.categorie("question"), useUnmergedTree = true)
        el(ReperesChamps.NOUVELLE.categorie("question")).toucher()
        el(ReperesChamps.NOUVELLE.modeSaisie).assert(hasText("Saisie complète", substring = true))
        el(ReperesChamps.NOUVELLE.plusDeDetails).assertDoesNotExist()
        el(ReperesChamps.NOUVELLE.urgente).assertExists()
    }

    /**
     * #179 : « Qui la lira » dit d'où viendra la règle avant la catégorie, puis la pastille et
     * la phrase que le serveur rend pour la catégorie et le lieu saisis.
     */
    @Test
    fun quiLaLiraSeDitDesLaCategorieChoisie() {
        val service = ouvrir()
        ecran.attendreTag(ReperesNouvelle.NOUVELLE, useUnmergedTree = true)
        el(ReperesNouvelle.NOUVELLE).toucher()
        ecran.attendreTag(ReperesChamps.NOUVELLE.categorie("panne"), useUnmergedTree = true)
        ecran.amenerTag(ReperesNouvelle.QUI_LA_LIRA).assert(hasText("Elle se dit dès la catégorie choisie.", substring = true))
        el(ReperesChamps.NOUVELLE.categorie("panne")).toucher()
        ecran.waitUntil(DELAI_MS) {
            ecran.onAllNodes(hasTestTag(ReperesNouvelle.QUI_LA_LIRA) and hasText("Lue par les copropriétaires", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(SaisieLecture("panne", listOf("copro")), synchronized(service.lectures) { service.lectures.last() })
    }

    /** Une saisie entamée ne se perd pas : « Annuler » demande d'abord. */
    @Test
    fun uneSaisieEntameeDemandeAvantDAbandonner() {
        ouvrir()
        ecran.attendreTag(ReperesNouvelle.NOUVELLE, useUnmergedTree = true)
        el(ReperesNouvelle.NOUVELLE).toucher()
        ecran.attendreTag(ReperesChamps.NOUVELLE.titre, useUnmergedTree = true)
        el(ReperesChamps.NOUVELLE.titre).performTextInput("Brouillon")
        ecran.onNode(hasText("Annuler")).toucher()
        ecran.attendreTag(ReperesNouvelle.ABANDONNER, useUnmergedTree = true)
        el(ReperesNouvelle.ABANDONNER).toucher()
        ecran.attendreDisparitionTag(ReperesChamps.NOUVELLE.titre, useUnmergedTree = true)
    }

    /** Au bandeau de la fiche : une Suite qui change l'état, puis — hors conseil — Répondre. */
    @Test
    fun laFicheAjouteUneSuiteEtRepond() {
        val service = ouvrirFiche(1)
        ecran.attendreTag(repereGeste(GesteAffaire.Suite), useUnmergedTree = true)
        el(repereGeste(GesteAffaire.Suite)).toucher()
        ecran.attendreTag(ReperesFeuilles.suiteEtat("en_cours"), useUnmergedTree = true)
        el(ReperesFeuilles.suiteEtat("en_cours")).toucher()
        el(ReperesFeuilles.SUITE_ENREGISTRER).toucher()
        ecran.attendreTexte("Statut mis à jour", useUnmergedTree = true)
        assertEquals("en_cours", service.suites.single().second.statut)
        el(repereGeste(GesteAffaire.Repondre)).toucher()
        ecran.attendreTag(ReperesFeuilles.REPONDRE_TEXTE, useUnmergedTree = true)
        el(ReperesFeuilles.REPONDRE_TEXTE).performTextInput("Merci !")
        el(ReperesFeuilles.REPONDRE_ENVOYER).toucher()
        ecran.attendreTexte("Réponse envoyée", useUnmergedTree = true)
        assertEquals(listOf(1 to "Merci !"), service.reponses)
    }

    /** « ⋯ » d'une entrée : la corriger (l'affaire n'a pas suivi : l'alerte propose l'état servi), puis la supprimer. */
    @Test
    fun uneEntreeDeLaSuiteSeCorrigeEtSeSupprime() {
        val service = ouvrirFiche(1)
        ecran.attendreTag(ReperesAffaires.gestesSuite(31), useUnmergedTree = true)
        el(ReperesAffaires.gestesSuite(31)).toucher()
        ecran.attendreTag(ReperesAffaires.gesteSuite(GesteSuite.Modifier), useUnmergedTree = true)
        el(ReperesAffaires.gesteSuite(GesteSuite.Modifier)).toucher()
        ecran.attendreTag(ReperesFeuilles.SUITE_TEXTE, useUnmergedTree = true)
        el(ReperesFeuilles.SUITE_TEXTE).performTextReplacement("Le syndic est prévenu. Relancé.")
        el(ReperesFeuilles.SUITE_ENREGISTRER).toucher()
        ecran.attendreTag(ReperesAffaires.APPLIQUER, useUnmergedTree = true)
        ecran.attendreTexte("L'affaire a avancé depuis", useUnmergedTree = true)
        el(ReperesAffaires.APPLIQUER).toucher()
        ecran.waitUntil(DELAI_MS) { service.suites.lastOrNull()?.second?.statut == "en_cours" }
        assertEquals(true, service.corrections.single().contenuHtml?.contains("Relancé"))
        el(ReperesAffaires.gestesSuite(31)).toucher()
        ecran.attendreTag(ReperesAffaires.gesteSuite(GesteSuite.Supprimer), useUnmergedTree = true)
        el(ReperesAffaires.gesteSuite(GesteSuite.Supprimer)).toucher()
        ecran.attendreTag(ReperesAffaires.CONFIRMER_SUPPRESSION, useUnmergedTree = true)
        el(ReperesAffaires.CONFIRMER_SUPPRESSION).toucher()
        ecran.waitUntil(DELAI_MS) { service.supprimees == listOf(31) }
    }

    /** ✏️ « Modifier » : seul le titre changé part. */
    @Test
    fun uneAffaireSeModifie() {
        val service = ouvrirFiche(1)
        ecran.attendreTag(repereGeste(GesteAffaire.Modifier), useUnmergedTree = true)
        el(repereGeste(GesteAffaire.Modifier)).toucher()
        ecran.attendreTag(ReperesChamps.MODIFIER.titre, useUnmergedTree = true)
        // Le champ est déjà rempli : on remplace son texte (une frappe s'écrirait au début).
        el(ReperesChamps.MODIFIER.titre).performTextReplacement("Fuite au local vélos")
        el(ReperesNouvelle.MODIFIER_ENREGISTRER).toucher()
        ecran.attendreTexte("Affaire modifiée", useUnmergedTree = true)
        assertEquals(listOf(EditionAffaire(titre = "Fuite au local vélos")), service.editions)
    }

    /**
     * #182 : « Modifier » offre les prestataires selon le droit servi — en ajouter un de la
     * résidence, le monter en tête, retirer l'autre ; seuls leurs identifiants partent.
     */
    @Test
    fun lesPrestatairesDUneAffaireSeChoisissent() {
        val service = ouvrirFiche(1)
        ecran.attendreTag(repereGeste(GesteAffaire.Modifier), useUnmergedTree = true)
        el(repereGeste(GesteAffaire.Modifier)).toucher()
        ecran.attendreTag(ReperesPrestatairesAffaire.ligne(AffairesSimulees.ASCENSEURS), useUnmergedTree = true)
        el(ReperesPrestatairesAffaire.AJOUTER).toucher()
        ecran.attendreTag(ReperesPrestatairesAffaire.choix(AffairesSimulees.PLOMBERIE), useUnmergedTree = true)
        el(ReperesPrestatairesAffaire.choix(AffairesSimulees.PLOMBERIE)).toucher()
        ecran.attendreTag(ReperesPrestatairesAffaire.ligne(AffairesSimulees.PLOMBERIE), useUnmergedTree = true)
        el(ReperesPrestatairesAffaire.retirer(AffairesSimulees.ASCENSEURS)).toucher()
        ecran.attendreDisparitionTag(ReperesPrestatairesAffaire.ligne(AffairesSimulees.ASCENSEURS), useUnmergedTree = true)
        el(ReperesNouvelle.MODIFIER_ENREGISTRER).toucher()
        ecran.attendreTexte("Affaire modifiée", useUnmergedTree = true)
        assertEquals(listOf(EditionAffaire(prestataires = listOf(5))), service.editions)
    }

    private companion object {
        const val DELAI_MS = 10_000L
    }
}
