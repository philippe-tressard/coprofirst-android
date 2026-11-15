// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.navigation

import androidx.compose.runtime.Composable
import fr.coprofirst.app.commun.Apparence
import fr.coprofirst.app.commun.GestesDeSortie
import fr.coprofirst.app.session.SessionOuverte

/**
 * Ce qu'un écran de menu reçoit : sa page, la session de la résidence, les droits du
 * compte, les gestes de sortie (à confirmer par `ConfirmationDeSortie`), le thème de
 * l'appareil et la barre du haut (`ActionsDeBarre`).
 */
data class ContexteDeMenu(
    val page: PageDeNavigation,
    val session: SessionOuverte,
    val droits: DroitsOnglet,
    val sortie: GestesDeSortie,
    val apparence: Apparence,
    /** La barre du haut : un écran y pose ses actions par `ActionsDeBarre`. */
    val barre: BarreDuHaut,
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

        // "tableau-de-bord" — #11

        // "mes-demandes" — #16

        // "residence" — #17

        // "annuaire" — #17

        // "mon-lot" — #18

        // "prestataires" — #19

        // "communaute" — #20

        // "faq" — #20

        // "profil" — #21

        // "notifications" — #22

        // "espace-cs" — #24

        // "delegations" — #25

        // "admin" — #26

    )

    /** L'écran de cette page, ou « Bientôt ». */
    @Composable
    fun Ecran(contexte: ContexteDeMenu) {
        val ecran = ecrans[contexte.page.id]
        if (ecran != null) ecran(contexte) else Bientot(contexte.page)
    }
}
