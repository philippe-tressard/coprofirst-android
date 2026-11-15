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

/** L'écran d'une page de menu. */
typealias EcranDeMenu = @Composable (ContexteDeMenu) -> Unit

/**
 * Ce qu'un menu INSCRIT au registre, depuis SON paquet (#109) : ses pages (id de la page
 * du site → l'écran) et les fiches d'éléments qu'il sait ouvrir (`Destination.Element`,
 * par nature : `affaire`, `sondage`…). Un menu l'écrit dans `fonctions/<menu>/Inscription.kt` :
 * ```
 * object InscriptionFaq : InscriptionDeMenu {
 *     override val ecrans: Map<String, EcranDeMenu> = mapOf("faq" to { contexte -> EcranFaq(contexte) })
 * }
 * ```
 * `scripts/generer_registre.py` les collecte au build (`RegistreGenere`) : aucun écran ne
 * touche un fichier commun, deux écrans ajoutés en parallèle ne se rencontrent jamais.
 */
interface InscriptionDeMenu {
    val ecrans: Map<String, EcranDeMenu> get() = emptyMap()
    val fiches: Map<String, FicheDeMenu> get() = emptyMap()
}

/** Ce que la couche commune inscrit elle-même : la fiche d'un sondage (`commun/detail/sondage`). */
private object InscriptionCommune : InscriptionDeMenu {
    override val fiches: Map<String, FicheDeMenu> = mapOf(
        "sondage" to FicheDeMenu(R.string.sondage_titre) { contexte, id ->
            id.toIntOrNull()?.let { fr.coprofirst.app.commun.detail.sondage.PageSondage(it, contexte.session) }
        },
    )
}

/**
 * LE registre des écrans de menu, comme l'iOS (`RegistreDesMenus`) : l'identifiant de la
 * page du site → l'écran qui l'ouvre ; la nature d'un élément → sa fiche. Une page sans
 * écran affiche « Bientôt ». Il se COMPOSE des inscriptions des menus (`RegistreGenere`,
 * généré) et de la couche commune ; une page ou une fiche inscrite deux fois arrête l'app
 * au premier accès — et les tests avant elle.
 */
object RegistreDesMenus {
    private val inscriptions: List<InscriptionDeMenu> = RegistreGenere.inscriptions + InscriptionCommune

    val ecrans: Map<String, EcranDeMenu> = fusionner("La page") { it.ecrans }

    val fiches: Map<String, FicheDeMenu> = fusionner("La fiche") { it.fiches }

    private fun <V> fusionner(quoi: String, lire: (InscriptionDeMenu) -> Map<String, V>): Map<String, V> {
        val tout = LinkedHashMap<String, V>()
        inscriptions.forEach { inscription ->
            lire(inscription).forEach { (cle, valeur) -> check(tout.put(cle, valeur) == null) { "$quoi « $cle » est inscrite deux fois" } }
        }
        return tout
    }

    /** L'écran de cette page, ou « Bientôt ». */
    @Composable
    fun Ecran(contexte: ContexteDeMenu) {
        val ecran = ecrans[contexte.page.id]
        if (ecran != null) ecran(contexte) else Bientot(contexte.page)
    }
}
