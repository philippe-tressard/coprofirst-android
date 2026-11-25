// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.fil

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.coprofirst.app.commun.attendreDescription
import fr.coprofirst.app.commun.attendreTag
import fr.coprofirst.app.commun.attendreTexte
import fr.coprofirst.app.commun.detail.sondage.ReperesSondage
import fr.coprofirst.app.commun.fil.TextesFil
import fr.coprofirst.app.commun.toucher
import fr.coprofirst.app.commun.toucherDescription
import fr.coprofirst.app.commun.toucherTag
import fr.coprofirst.app.entree.NavigationEntree
import fr.coprofirst.app.entree.entrerDansLaResidence
import fr.coprofirst.app.entree.ServeurDeTest
import fr.coprofirst.app.navigation.fermerLaFiche
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * Le fil à l'écran, contre un serveur local : la connexion, puis la première page de la
 * résidence — ses cartes, comme les tests d'interface de l'iOS (`FilUITests` : repères
 * `fil.<type>:<id>`, `resume`). Tout est inventé : aucune personne.
 */
@RunWith(AndroidJUnit4::class)
class FilInterfaceTest {
    @get:Rule
    val ecran = createComposeRule()
    private val serveur = ServeurDeTest()

    @Before
    fun ouvrir() {
        serveur.flux = fil(Instant.now())
        ecran.setContent { NavigationEntree(serveur.apparence(), serveur.parcours()) }
        serveur.residence("Les Glycines")
        ecran.entrerDansLaResidence("residence.example.com", "Les Glycines") {
            serveur.repondre(200) // POST /auth/login
            serveur.repondre(404) // GET /auth/me : sans profil, la barre reste celle de tous
        }
    }

    @After
    fun fermer() = serveur.close()

    /** Les cartes du jour, sous leur en-tête ; l'affaire s'ouvre par la porte (sa fiche, #16), le résumé aussi. */
    @Test
    fun leFilMontreSesCartes() {
        ecran.attendreTag("fil.ticket_ouvert:41")
        ecran.onNodeWithTag("fil.ticket_ouvert:41").assertIsDisplayed().assertHasClickAction()
        ecran.attendreTexte("AUJOURD'HUI")
        ecran.attendreDescription("Urgent, Fuite d'eau au plafond du parking")
        ecran.attendreDescription("Lieu : Parking")
        ecran.attendreDescription("Niveau -1, place 12.")
        ecran.onNodeWithTag("fil.point_conseil:2").assertIsDisplayed().assertHasClickAction()
    }

    /** Un élément sans écran natif s'ouvre sur son résumé, avec « Voir sur le site ». */
    @Test
    fun leResumeSOuvreDUnToucher() {
        ecran.attendreTag("fil.point_conseil:2")
        ecran.toucherTag("fil.point_conseil:2")
        ecran.attendreTag(REPERE_RESUME)
        ecran.attendreTexte("Ordre du jour : ascenseur, ravalement.")
        ecran.attendreTexte("Voir sur le site")
    }

    /**
     * Une carte sondage ouvre la fiche du sondage, comme l'iOS (`SondageView`) — poussée au téléphone,
     * dans sa colonne sur la tablette (#69) ; le retour la ferme.
     */
    @Test
    fun leSondageSOuvreSurSaFiche() {
        ecran.attendreTag("fil.sondage_ouvert:3")
        serveur.fixes["/api/sondages/3"] = SONDAGE
        serveur.repondre(404) // GET /auth/me, lu avec la fiche : sans compte, « Signaler » partout
        ecran.onNodeWithTag("fil.sondage_ouvert:3").assertHasClickAction().toucher()
        ecran.attendreTag(ReperesSondage.QUESTION)
        ecran.attendreTexte("Samedi 6 juin")
        ecran.attendreTexte("Sondage") // le titre de la barre du haut
        ecran.fermerLaFiche()
        ecran.waitUntil(DELAI_MS) { ecran.onAllNodes(hasTestTag(ReperesSondage.QUESTION)).fetchSemanticsNodes().isEmpty() }
    }

    internal companion object {
        const val DELAI_MS = 10_000L

        /** GET /sondages/3 à la forme du schéma (`SondageDetail`). */
        const val SONDAGE = """{"id": 3, "question": "Quel jour pour la fête des voisins ?", "cloture": false,
            "cree_le": "2026-11-08T09:00:00", "resultats_publics": true, "resultats_visibles": true,
            "options": [{"id": 1, "libelle": "Samedi 6 juin", "ordre": 0, "nb_votes": 14, "part": 100}], "commentaires": []}"""

        /** GET /flux à la forme du schéma (`FluxResponse`), daté de ce matin pour « Aujourd'hui ». */
        fun fil(maintenant: Instant): String {
            val tot = maintenant.minusSeconds(60)
            val plusTot = maintenant.minusSeconds(120)
            return """{"delai_recent_jours": 7, "items": [
                {"type": "ticket_ouvert", "id": "41", "date": "$tot", "titre": "Fuite d'eau au plafond du parking",
                 "detail": "<p>Niveau -1, place 12.</p>", "badges": ["urgent"], "lien": "/tickets/41",
                 "meta": {"ticket_id": 41, "affaire": {"natures": ["activite"], "perimetre_libelle": "Parking"}}},
                {"type": "point_conseil", "id": "2", "date": "$plusTot", "titre": "Point avec le syndic",
                 "detail": "<p>Ordre du jour : ascenseur, ravalement.</p>", "lien": "/points-conseil", "meta": {}},
                {"type": "sondage_ouvert", "id": "3", "date": "$plusTot", "titre": "Quel jour pour la fête des voisins ?",
                 "lien": "/sondages/3", "meta": {"sondage_id": 3}}
            ]}"""
        }
    }
}
