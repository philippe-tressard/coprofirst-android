// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fr.coprofirst.app.ThemeCoproFirst
import fr.coprofirst.app.charte.Palettes
import fr.coprofirst.app.commun.GestesDeSortie
import fr.coprofirst.app.commun.attendreDisparition
import fr.coprofirst.app.commun.attendreDisparitionTag
import fr.coprofirst.app.commun.attendreTag
import fr.coprofirst.app.commun.attendreTexte
import fr.coprofirst.app.commun.formats.FormatsDuSite
import fr.coprofirst.app.commun.miseenpage.REPERE_COLONNE
import fr.coprofirst.app.commun.residenceSimulee
import fr.coprofirst.app.commun.toucherTag
import fr.coprofirst.app.entree.ServeurDeTest
import fr.coprofirst.app.fonctions.fil.FilInterfaceTest
import fr.coprofirst.app.fonctions.fil.REPERE_EPHEMERIDE
import fr.coprofirst.app.fonctions.fil.REPERE_JOUR
import fr.coprofirst.app.fonctions.fil.REPERE_SEMAINE
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant

/**
 * #242, comme le carnet de l'iOS (coprofirst-ios#119) : un pliable déplié EN LIVRE — les
 * intercalaires sur la tranche, ni barre du bas ni barre latérale ; la liste à gauche du pli, la
 * fiche à droite ; et le marque-page, qui rouvre l'écran là où on l'avait refermé. Le pli est
 * IMPOSÉ (`LocalPosture`) : joué sur l'émulateur du téléphone, qui n'en a pas.
 */
@RunWith(AndroidJUnit4::class)
class CarnetInterfaceTest {
    @get:Rule
    val ecran = createComposeRule()
    private val serveur = ServeurDeTest()

    private val sortie = object : GestesDeSortie {
        override fun deconnecter() = Unit
        override fun oublierTout() = Unit
    }
    private val ecranDuTest = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics
    private val densite = ecranDuTest.density

    /**
     * Le livre ouvert tient dans l'écran de l'émulateur — 320 dp en CI, plus au poste — pour que les
     * intercalaires y restent : le carnet ne dépend pas de la largeur, seulement du pli.
     */
    private val largeur = minOf(400f, ecranDuTest.widthPixels / densite)

    /** Le pli, en pixels de la fenêtre : aux 45 % du livre, 16 dp de large. */
    private val gauche = (largeur * 0.45f * densite).toInt()
    private val droite = gauche + (16 * densite).toInt()
    private val posture = Posture.depuis(true, listOf(Pli(vertical = true, gauche = gauche, droite = droite, aPlat = false)))

    private val disque = mutableMapOf<String, String>()
    private val marques = MarquesPages(
        object : StockageMarquePage {
            override fun lire(cle: String) = disque[cle]

            override fun ecrire(cle: String, valeur: String?) {
                if (valeur == null) disque.remove(cle) else disque[cle] = valeur
            }
        },
        pliable = true,
    )

    /** L'écran affiché ; `false` le temps de le refermer (l'app arrêtée, rien de sauvé). */
    private var affiche by mutableStateOf(true)

    private fun ouvrir(flux: String = FilInterfaceTest.fil(Instant.now())) {
        serveur.flux = flux
        val session = serveur.session(residenceSimulee(RESIDENCE, "Les Glycines"))
        ecran.setContent {
            CompositionLocalProvider(LocalPosture provides posture, LocalMarquesPages provides marques) {
                ThemeCoproFirst(Palettes.pour(null), sombre = false) {
                    Box(Modifier.wrapContentSize(Alignment.TopStart, unbounded = true).requiredSize(largeur.dp, 700.dp)) {
                        if (affiche) EcranResidence(session, sortie, serveur.apparence())
                    }
                }
            }
        }
        ecran.attendreTag(CARTE)
    }

    @After
    fun fermer() = serveur.close()

    @Test
    fun lesIntercalairesRemplacentLesBarres() {
        ouvrir()
        ecran.attendreTag("carnet.${LibellesBarre.ID_FIL}")
        ecran.attendreTag("carnet.profil")
        ecran.attendreTag("carnet.a-propos")
        ecran.onNodeWithTag("carnet.${LibellesBarre.ID_FIL}").assertIsSelected()
        ecran.attendreDisparition(hasTestTag("barre.profil"))
        ecran.attendreDisparition(hasText("Plus"))
        ecran.toucherTag("carnet.a-propos")
        ecran.attendreTexte("À propos")
        ecran.onNodeWithTag("carnet.a-propos").assertIsSelected()
        ecran.attendreDisparition(hasContentDescription("Retour"))
    }

    @Test
    fun laListeEtLaFicheSeTiennentDePartEtDAutreDuPli() {
        ouvrir()
        ecran.attendreTexte("Aucune sélection")
        ecran.toucherTag(CARTE)
        ecran.attendreDisparition(hasText("Aucune sélection"))
        val carte = ecran.onNodeWithTag(CARTE).fetchSemanticsNode().boundsInWindow
        val colonne = ecran.onNodeWithTag(REPERE_COLONNE).fetchSemanticsNode().boundsInWindow
        assertTrue("la carte déborde le pli : ${carte.right} > $gauche", carte.right <= gauche + 1)
        assertTrue("la fiche commence avant le pli : ${colonne.left} < $droite", colonne.left >= droite - 1)
    }

    /** Refermé sur une fiche, rouvert sans rien de sauvé (l'app arrêtée) : la même fiche. */
    @Test
    fun leMarquePageRouvreLaFicheOuIlAvaitReferme() {
        ouvrir()
        ecran.toucherTag(CARTE)
        ecran.attendreDisparition(hasText("Aucune sélection"))
        ecran.runOnUiThread { affiche = false }
        ecran.waitForIdle()
        assertTrue(marques.lire(RESIDENCE)?.ficheId != null)
        ecran.runOnUiThread { affiche = true }
        ecran.attendreTag(CARTE)
        ecran.attendreDisparition(hasText("Aucune sélection"))
        ecran.onNodeWithTag(CARTE).assertIsSelected()
    }

    /**
     * Le Fil du carnet : une journée par double page — l'éphéméride, les cartes du jour seules ;
     * « Veille » mène au jour d'activité d'avant, « Lendemain » en revient ; refermé puis rouvert,
     * le carnet reprend au jour où on l'avait laissé (le marque-page).
     */
    @Test
    fun leFilTourneSesPagesEtLeMarquePageGardeLeJour() {
        val maintenant = Instant.now()
        ouvrir(deuxJours(maintenant))
        ecran.attendreTag(REPERE_EPHEMERIDE)
        ecran.attendreDisparitionTag(AVANT)
        ecran.onNodeWithTag("carnet.lendemain").assertIsNotEnabled()
        ecran.toucherTag("carnet.veille")
        ecran.attendreTag(AVANT)
        ecran.attendreDisparitionTag(CARTE)
        ecran.onNodeWithTag("carnet.veille").assertIsNotEnabled()
        ecran.runOnUiThread { affiche = false }
        ecran.waitForIdle()
        ecran.runOnUiThread { affiche = true }
        ecran.attendreTag(AVANT)
        ecran.toucherTag("carnet.lendemain")
        ecran.attendreTag(CARTE)
        ecran.attendreDisparitionTag(AVANT)
    }

    /**
     * #286, comme `CarnetUITests` de l'iOS (coprofirst-ios#277) : PINCER la page du jour ouvre sa
     * semaine sur toute la double page, chaque carte au jour servi, « Rien ce jour » ailleurs ;
     * ÉCARTER les doigts sur un jour le rouvre.
     */
    @Test
    fun pincerOuvreLaSemaineEtEcarterRameneAuJour() {
        val maintenant = Instant.now()
        ouvrir(semaineEtAvant(maintenant))
        pincer(REPERE_JOUR, ferme = true)
        ecran.attendreTag(REPERE_SEMAINE)
        ecran.attendreTag(CARTE)
        ecran.attendreTexte("Rien ce jour")
        ecran.attendreDisparitionTag(REPERE_JOUR)
        ecran.attendreDisparitionTag(REPERE_COLONNE)
        pincer("$REPERE_SEMAINE.${FormatsDuSite.jourIso(maintenant)}", ferme = false)
        ecran.attendreTag(REPERE_EPHEMERIDE)
        ecran.attendreDisparitionTag(REPERE_SEMAINE)
        ecran.attendreTexte("Aucune sélection")
    }

    /**
     * La semaine se tourne au pied de page, jamais avant celle du plus ancien jour du fil ; l'en-tête
     * d'un jour le rouvre ; une carte touchée s'ouvre sur la page de son jour, sa fiche à droite du pli.
     */
    @Test
    fun laSemaineSeTourneEtSesJoursEtSesCartesSOuvrent() {
        val maintenant = Instant.now()
        ouvrir(semaineEtAvant(maintenant))
        pincer(REPERE_JOUR, ferme = true)
        ecran.attendreTag(REPERE_SEMAINE)
        ecran.toucherTag("$REPERE_SEMAINE.precedente")
        ecran.attendreTag(AVANT)
        ecran.onNodeWithTag("$REPERE_SEMAINE.precedente").assertIsNotEnabled()
        ecran.toucherTag("$REPERE_SEMAINE.${FormatsDuSite.jourIso(semainePrecedente(maintenant))}")
        ecran.attendreTag(REPERE_EPHEMERIDE)
        ecran.attendreTag(AVANT)
        ecran.attendreDisparitionTag(CARTE)
        pincer(REPERE_JOUR, ferme = true)
        ecran.attendreTag(REPERE_SEMAINE)
        ecran.toucherTag("$REPERE_SEMAINE.suivante")
        ecran.toucherTag(CARTE)
        ecran.attendreTag(REPERE_JOUR)
        ecran.attendreDisparition(hasText("Aucune sélection"))
        ecran.onNodeWithTag(CARTE).assertIsSelected()
    }

    /** Deux doigts sur `repere` : rapprochés (`ferme`) ou écartés, de part et d'autre de son centre. */
    private fun pincer(repere: String, ferme: Boolean) {
        ecran.attendreTag(repere)
        ecran.onNodeWithTag(repere).performTouchInput {
            val loin = minOf(width / 2f - 4f, 70f * densite)
            val pres = 6f * densite
            val (debut, fin) = if (ferme) loin to pres else pres to loin
            pinch(center - Offset(debut, 0f), center - Offset(fin, 0f), center + Offset(debut, 0f), center + Offset(fin, 0f))
        }
    }

    /**
     * Le même jour, une semaine plus tôt : toujours dans la semaine d'avant, quel que soit le jour.
     * Huit jours tombaient, un lundi, deux semaines plus tôt (les semaines partent du lundi,
     * `SemaineDuFil.lundi`) : le test échouait chaque lundi, sur main aussi (23/11/2026).
     */
    private fun semainePrecedente(maintenant: Instant): Instant = maintenant.minus(Duration.ofDays(7))

    /** L'affaire d'aujourd'hui (41) et le sondage d'une semaine avant (3), chacun à son jour SERVI. */
    private fun semaineEtAvant(maintenant: Instant): String {
        val avant = semainePrecedente(maintenant)
        return """{"delai_recent_jours": 7, "items": [
        {"type": "ticket_ouvert", "id": "41", "date": "${maintenant.minusSeconds(60)}", "titre": "Fuite d'eau au plafond du parking",
         "jour": "${FormatsDuSite.jourIso(maintenant)}", "meta": {"ticket_id": 41, "affaire": {"natures": ["activite"]}}},
        {"type": "sondage_ouvert", "id": "3", "date": "$avant", "titre": "Quel jour pour la fête des voisins ?",
         "jour": "${FormatsDuSite.jourIso(avant)}", "lien": "/sondages/3", "meta": {"sondage_id": 3}}
    ]}"""
    }

    /** Une affaire d'aujourd'hui (41), un sondage de trois jours avant (3) : deux jours d'activité. */
    private fun deuxJours(maintenant: Instant): String = """{"delai_recent_jours": 7, "items": [
        {"type": "ticket_ouvert", "id": "41", "date": "${maintenant.minusSeconds(60)}", "titre": "Fuite d'eau au plafond du parking",
         "meta": {"ticket_id": 41, "affaire": {"natures": ["activite"], "perimetre_libelle": "Parking"}}},
        {"type": "sondage_ouvert", "id": "3", "date": "${maintenant.minus(Duration.ofDays(3))}", "titre": "Quel jour pour la fête des voisins ?",
         "lien": "/sondages/3", "meta": {"sondage_id": 3}}
    ]}"""

    private companion object {
        /** Le sondage du jour d'avant. */
        const val AVANT = "fil.sondage_ouvert:3"

        const val RESIDENCE = "https://residence.exemple.test"

        /** La première carte du fil (l'affaire 41). */
        const val CARTE = "fil.ticket_ouvert:41"
    }
}
