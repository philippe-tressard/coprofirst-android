// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.navigation

import androidx.compose.runtime.Composable
import fr.coprofirst.app.R
import fr.coprofirst.app.commun.Apparence
import fr.coprofirst.app.commun.GestesDeSortie
import fr.coprofirst.app.session.SessionOuverte

/**
 * Ce qu'un écran de menu reçoit : sa page, la session de la résidence, les droits du
 * compte, les gestes de sortie (à confirmer par `ConfirmationDeSortie`), le thème de
 * l'appareil, la barre du haut (`ActionsDeBarre`) et la porte (`ouvrir`).
 */
data class ContexteDeMenu(
    val page: PageDeNavigation,
    val session: SessionOuverte,
    val droits: DroitsOnglet,
    val sortie: GestesDeSortie,
    val apparence: Apparence,
    /** La barre du haut : un écran y pose ses actions par `ActionsDeBarre`. */
    val barre: BarreDuHaut,
    /**
     * La porte (#85) : ouvre une page, la fiche d'un élément ou la cloche. `false` : rien ne
     * la porte dans l'app (page que le compte ne voit pas, fiche non déclarée) — l'écran
     * garde alors son repli, « Voir sur le site » par exemple.
     */
    val ouvrir: (Destination) -> Boolean = { false },
)

/**
 * LE registre des écrans de menu, comme l'iOS (`RegistreDesMenus`) : l'identifiant de la
 * page du site → l'écran qui l'ouvre. Une page sans ligne affiche « Bientôt ».
 *
 * RÈGLE (docs/COMPOSANTS.md) : un écran = un paquet `fr.coprofirst.app.fonctions.<menu>`
 * + SA ligne ici. Chaque menu a sa ligne réservée d'avance, entre deux lignes vides :
 * deux sessions parallèles ne touchent jamais la même ligne, et la fusion reste triviale.
 */
object RegistreDesMenus {
    val ecrans: Map<String, @Composable (ContexteDeMenu) -> Unit> = mapOf(
        // UNE ligne réservée par menu : la session du menu remplace SA ligne par
        //     "profil" to { contexte -> EcranProfil(contexte) },
        // et ne touche à aucune autre.

        "tableau-de-bord" to { contexte -> fr.coprofirst.app.fonctions.fil.EcranFil(contexte) },

        // "mes-demandes" — #16

        "residence" to { contexte -> fr.coprofirst.app.fonctions.residence.EcranMaResidence(contexte) },

        "annuaire" to { contexte -> fr.coprofirst.app.fonctions.annuaire.EcranAnnuaire(contexte) },

        "mon-lot" to { contexte -> fr.coprofirst.app.fonctions.monlot.EcranMonLot(contexte) },

        // "prestataires" — #19

        "communaute" to { contexte -> fr.coprofirst.app.fonctions.communaute.EcranCommunaute(contexte) },

        "faq" to { contexte -> fr.coprofirst.app.fonctions.faq.EcranFaq(contexte) },

        "profil" to { contexte -> fr.coprofirst.app.fonctions.profil.EcranProfil(contexte) },

        "notifications" to { contexte -> fr.coprofirst.app.fonctions.notifications.EcranNotifications(contexte) },

        "espace-cs" to { contexte -> fr.coprofirst.app.fonctions.espacecs.EcranEspaceCS(contexte) },

        "delegations" to { contexte -> fr.coprofirst.app.fonctions.delegations.EcranDelegations(contexte) },

        // "admin" — #26

    )

    /**
     * Les fiches d'éléments, par nature (`Destination.Element`) : ce qu'un menu déclare
     * savoir ouvrir, pour qu'un autre écran (le fil, une notification) y mène sans dépendre
     * de son code. Même règle qu'au-dessus : UNE ligne réservée par nature, que la session
     * du menu remplace par
     *     "affaire" to FicheDeMenu(R.string.affaire_titre) { contexte, id -> DetailAffaire(contexte, id) },
     */
    val fiches: Map<String, FicheDeMenu> = mapOf(
        // "affaire" — #16

        "sondage" to FicheDeMenu(R.string.sondage_titre) { contexte, id ->
            id.toIntOrNull()?.let { fr.coprofirst.app.commun.detail.sondage.PageSondage(it, contexte.session) }
        },

    )

    /** L'écran de cette page, ou « Bientôt ». */
    @Composable
    fun Ecran(contexte: ContexteDeMenu) {
        val ecran = ecrans[contexte.page.id]
        if (ecran != null) ecran(contexte) else Bientot(contexte.page)
    }
}
