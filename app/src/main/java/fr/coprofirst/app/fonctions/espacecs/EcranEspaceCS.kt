// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.espacecs

import androidx.compose.runtime.Composable
import fr.coprofirst.app.R
import fr.coprofirst.app.commun.onglets.CadreAOnglets
import fr.coprofirst.app.commun.onglets.ContexteOnglet
import fr.coprofirst.app.commun.onglets.PageAOnglets
import fr.coprofirst.app.navigation.ContexteDeMenu

/**
 * L'Espace conseil syndical (page `espace-cs`, #24), comme l'iOS (`EspaceCSView`) : six
 * onglets du site, dans son ordre, filtrés par le cadre selon les droits du compte. Un
 * onglet que l'app ne porte pas encore dit « Bientôt » (le cadre).
 *
 * Le service de la page est découpé PAR ONGLET, comme l'iOS (`ServiceComptesCS`,
 * `ServiceBadgesCS`…) : la file « Comptes & accès » sert aussi « À traiter » du
 * Paramétrage (#26), qui n'en prend que sa part.
 */
@Composable
fun EcranEspaceCS(contexte: ContexteDeMenu) = CadreAOnglets(contexte, PageEspaceCS)

/** La table des onglets de l'Espace CS : l'identifiant du site → son écran, une ligne par onglet. */
val PageEspaceCS = PageAOnglets(
    "espace-cs",
    mapOf<String, @Composable (ContexteOnglet) -> Unit>(
        "validations" to { onglet -> OngletValidations(onglet) },

        "badges" to { onglet -> OngletBadges(onglet) },
    ),
    reserve = R.string.espacecs_reserve,
)

/** Les repères des tests d'interface : les identifiants d'accessibilité de l'iOS. */
object ReperesEspaceCS {
    const val COMPTEUR_COMPTES = "cs.compteur.comptes"
    const val COMPTEUR_COMMANDES = "cs.compteur.commandes"
    fun compte(id: Int) = "cs.compte.$id"
    fun commande(id: Int) = "cs.commande.$id"
    const val REJET_CONFIRMER = "cs.rejet.confirmer"
    const val COMMANDE_CODES = "cs.commande.codes"
    const val COMMANDE_ACCEPTER = "cs.commande.accepter"
    const val NOUVEL_ARRIVANT = "cs.validation.nouvel-arrivant"
    const val VALIDATION_ENREGISTRER = "cs.validation.enregistrer"

    const val BADGES_RECHERCHE = "cs.badges.recherche"
    const val BADGES_COMPTE = "cs.badges.compte"
    fun badgesType(code: String) = "cs.badges.type.$code"
    fun badge(id: String) = "cs.badge.$id"
}
