// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.affaires

import android.view.KeyEvent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
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
import fr.coprofirst.app.commun.clavier.LocalRaccourcisClavier
import fr.coprofirst.app.commun.clavier.Raccourci
import fr.coprofirst.app.commun.clavier.RaccourcisClavier
import fr.coprofirst.app.commun.formats.FormatsDuSite
import fr.coprofirst.app.commun.toucher
import fr.coprofirst.app.navigation.ActionsDeclarees
import fr.coprofirst.app.navigation.BarreDuHaut
import fr.coprofirst.app.navigation.DroitsOnglet
import fr.coprofirst.app.session.Installation
import fr.coprofirst.app.session.SessionOuverte
import fr.coprofirst.app.session.SessionsParHote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.temporal.ChronoUnit

/** « Mes affaires », comme les tests d'interface de l'iOS (`AffairesUITests`). */
@RunWith(AndroidJUnit4::class)
class AffairesInterfaceTest {
    @get:Rule
    val ecran = createComposeRule()

    /** Les fiches que les cartes ont demandées à la porte. */
    private val fiches = mutableListOf<Int>()
    private val session = SessionOuverte(Installation("https://residence.exemple.test", "Les Glycines"), SessionsParHote())

    /** Les raccourcis du clavier, comme l'activité les tient (#69). */
    private val raccourcis = RaccourcisClavier()

    private fun ouvrir(droits: DroitsOnglet = DroitsOnglet(cs = true, proprioOuCS = true), saisie: String? = null): AffairesSimulees {
        val service = AffairesSimulees()
        val barre = BarreDuHaut()
        ecran.setContent {
            ThemeCoproFirst {
                CompositionLocalProvider(LocalSaisieAffaire provides saisie, LocalRaccourcisClavier provides raccourcis) {
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
        ecran.attendreTexte("Ouvert →\u00A0Chez le syndic", useUnmergedTree = true)
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
     * #69, `AffairesUITests/testCommandeNOuvreUneNouvelleAffaire` de l'iOS : au clavier d'une
     * tablette, Ctrl+N ouvre « Nouvelle affaire », et Ctrl+R est offert (la liste s'actualise).
     */
    @Test
    fun ctrlNOuvreUneNouvelleAffaire() {
        ouvrir()
        ecran.attendreTag(ReperesNouvelle.NOUVELLE, useUnmergedTree = true)
        ecran.runOnIdle {
            assertEquals(listOf(Raccourci.NouvelleAffaire, Raccourci.Actualiser), raccourcis.offerts())
            assertTrue(raccourcis.declencher(KeyEvent.KEYCODE_N, ctrl = true))
        }
        ecran.attendreTag(ReperesChamps.NOUVELLE.categorie("panne"), useUnmergedTree = true)
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

    /**
     * Pendant de coprofirst-ios#309 : « Ajouter un prestataire » se touche sur TOUTE sa rangée,
     * aussi large que celles des prestataires au-dessus d'elle — pas seulement sur son libellé, ce
     * qui laissait vide la droite de la rangée (une feuille de tablette la rend très large).
     */
    @Test
    fun toutLaRangeeAjouterUnPrestataireSeTouche() {
        ouvrirFiche(1)
        ecran.attendreTag(repereGeste(GesteAffaire.Modifier), useUnmergedTree = true)
        el(repereGeste(GesteAffaire.Modifier)).toucher()
        ecran.attendreTag(ReperesPrestatairesAffaire.ligne(AffairesSimulees.ASCENSEURS), useUnmergedTree = true)
        val ajouter = ecran.amenerTag(ReperesPrestatairesAffaire.AJOUTER, useUnmergedTree = true)
        val rangee = el(ReperesPrestatairesAffaire.ligne(AffairesSimulees.ASCENSEURS)).fetchSemanticsNode().boundsInRoot
        val bouton = ajouter.fetchSemanticsNode().boundsInRoot
        assertTrue("la rangée « Ajouter » s'arrête à ${bouton.right}, avant ${rangee.right}", bouton.right >= rangee.right - 1f)
        // Un toucher à DROITE de la rangée, loin du libellé, ouvre la liste.
        ajouter.performTouchInput { click(Offset(width - 12f, height / 2f)) }
        ecran.attendreTag(ReperesPrestatairesAffaire.choix(AffairesSimulees.PLOMBERIE), useUnmergedTree = true)
    }

    private companion object {
        const val DELAI_MS = 10_000L
    }
}
