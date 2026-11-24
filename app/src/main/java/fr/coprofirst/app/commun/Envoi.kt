// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import fr.coprofirst.app.session.ErreurService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * La machine d'envoi d'un geste au serveur (#324) — une feuille de saisie, un formulaire, un
 * bouton de ligne : UN envoi à la fois, `enCours` le temps de l'envoi (pour `BoutonsFeuille`,
 * `BoutonPlein(enCours = …)`, le geste d'une feuille du profil), l'`ErreurService` gardée pour
 * l'écran (`envoi.erreur?.let { MessageErreur(it) }`), le drapeau rendu quoi qu'il arrive.
 * L'iOS l'écrit dans chaque feuille (`envoyer()` privé) ; ici elle s'écrit une fois,
 * `verifier-depot.sh` refuse le drapeau recopié.
 *
 * `lancer(action)` : `action` est la suite du succès, écrite par l'écran dans son ordre
 * (l'appel, puis l'avis, la fermeture…) ; elle lève une `ErreurService` pour dire l'échec.
 * Le drapeau se lève AVANT de partir, d'un seul tenant avec la garde du double envoi : deux
 * touchers dans la même image n'envoient qu'une fois.
 */
@Stable
class Envoi(private val portee: CoroutineScope) {
    /** Un envoi est parti et n'a pas encore répondu. */
    var enCours by mutableStateOf(false)
        private set

    /** L'échec du dernier envoi ; un envoi réussi l'efface. */
    var erreur by mutableStateOf<ErreurService?>(null)
        private set

    /**
     * Envoyer, si rien n'est en cours (sinon : rien). `effacer` : l'erreur précédente
     * disparaît dès le départ — sinon elle reste à l'écran jusqu'au succès. `echec` : ce que
     * l'écran fait de plus d'une erreur (la dire par l'avis, remettre une case, un retour
     * haptique) ; elle est gardée dans `erreur` dans tous les cas.
     */
    fun lancer(effacer: Boolean = false, echec: (ErreurService) -> Unit = {}, action: suspend () -> Unit) {
        if (enCours) return
        enCours = true
        if (effacer) erreur = null
        portee.launch {
            try {
                action()
                erreur = null
            } catch (e: ErreurService) {
                erreur = e
                echec(e)
            } finally {
                enCours = false
            }
        }
    }
}

/** L'envoi d'un écran, lié à sa composition : il s'annule quand l'écran se quitte. */
@Composable
fun rememberEnvoi(): Envoi {
    val portee = rememberCoroutineScope()
    return remember(portee) { Envoi(portee) }
}
