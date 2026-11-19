// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.session

import kotlinx.coroutines.CancellationException

/**
 * Ce qui suit le cycle d'une résidence sur l'appareil (#150) : les notifications push
 * (`NotificationsPush`, #280), la liaison avec la montre (#207). Un SEUL point d'accroche :
 * le parcours d'entrée prévient ses abonnés, aucun ne se branche sur la session à part.
 */
interface AbonneSession {
    /** Une résidence s'ouvre, sa session en main : connexion, session gardée rouverte, ouverture d'office. */
    suspend fun residenceOuverte(session: SessionOuverte)

    /**
     * Elle se ferme : déconnexion, « Oublier toutes les résidences », session refusée par le
     * serveur. Appelé AVANT la déconnexion, pour qu'un retrait parte encore authentifié ; sur
     * une session refusée, l'appel au serveur échoue — l'abonné oublie sa part quand même.
     */
    suspend fun residenceFermee(session: SessionOuverte)
}

/**
 * Les abonnés de l'app, prévenus dans l'ordre. L'échec de l'un n'arrête ni les suivants ni
 * le geste de session : chacun tient déjà la cloche, ou le serveur, pour chemin de secours.
 * La liste s'écrit une fois, là où l'app se monte (`FabriqueParcoursEntree`).
 */
class AbonnesSession(private val abonnes: List<AbonneSession>) {
    suspend fun ouverte(session: SessionOuverte) = prevenir { residenceOuverte(session) }

    suspend fun fermee(session: SessionOuverte) = prevenir { residenceFermee(session) }

    private suspend fun prevenir(geste: suspend AbonneSession.() -> Unit) {
        for (abonne in abonnes) {
            try {
                abonne.geste()
            } catch (annulee: CancellationException) {
                throw annulee
            } catch (_: Exception) {
                // Rien ne se journalise (spec push N9) : l'abonné suivant est prévenu.
            }
        }
    }

    companion object {
        /** Aucun abonné : les tests du parcours, et tout parcours monté sans l'appareil. */
        val Aucun = AbonnesSession(emptyList())
    }
}
