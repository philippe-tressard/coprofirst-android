// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.apercu

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * L'aperçu SERVI d'une saisie non enregistrée, comme `EtatApercu` de l'iOS : un formulaire
 * ne compose pas une règle d'affichage sur ce qu'on saisit, il la DEMANDE au serveur, qui
 * applique celle de l'objet enregistré — ici, le lieu choisi (`POST /perimetres/libelle`).
 *
 * Le mécanisme est écrit ICI, une fois ; un écran l'emploie par `ApercuServi`. Les
 * conditions arbitrées le 11/11/2026 (coprofirst#2633) :
 * - **le délai** (`DELAI_MS`) : une saisie neuve annule la demande de la précédente, qui
 *   n'est pas encore partie — une rafale de touchers fait UNE demande ;
 * - **une réponse périmée est ignorée** : rangée sous SA saisie, elle ne se montre que si
 *   cette saisie est encore la courante ;
 * - **la dernière valeur reste** pendant l'attente, sans clignoter : `valeur` ne repasse
 *   jamais par `null` ;
 * - **un échec se tait** : la dernière valeur servie reste, la saisie n'est jamais bloquée ;
 * - **une valeur déjà servie** — avec l'objet (`connue`), ou plus tôt dans la saisie — ne se
 *   redemande pas, et une demande en route pour la même saisie ne repart pas.
 *
 * ⚠️ Divergence déclarée : l'iOS ne fait pas partir la demande hors ligne (`Reseau`) ni en
 * consultation ; l'Android n'a pas encore ces états — la demande part, échoue, et se tait.
 *
 * Sur le fil principal seulement (celui de Compose) : `pause` est le délai, un test le
 * remplace ou le laisse au temps virtuel.
 */
class EtatApercu<E : Any, S : Any>(
    connue: Pair<E, S>? = null,
    private val pause: suspend () -> Unit = { delay(DELAI_MS) },
) {
    /** Ce que l'écran montre : la sortie de la saisie courante, ou la dernière montrée. */
    var valeur: S? by mutableStateOf(connue?.second)
        private set

    private var courante: E? = connue?.first
    private val servies = HashMap<E, S>().apply { connue?.let { put(it.first, it.second) } }
    private var enCours: E? = null
    private var demande: Job? = null

    /** Le numéro de la dernière demande : une demande remplacée ne touche plus à rien. */
    private var jeton = 0

    /** La saisie est maintenant `entree` ; la demande part dans `portee`, après le délai. */
    fun saisie(entree: E, portee: CoroutineScope, demander: suspend (E) -> S) {
        courante = entree
        servies[entree]?.let { servie ->
            abandonner()
            montrer(servie)
            return
        }
        if (enCours == entree) return
        abandonner()
        val numero = ++jeton
        enCours = entree
        demande = portee.launch {
            try {
                pause()
                recevoir(demander(entree), entree, numero)
            } catch (annulee: CancellationException) {
                throw annulee
            } catch (_: Exception) {
                // Refusée ou injoignable : la dernière valeur reste, rien n'est bloqué.
                if (jeton == numero) enCours = null
            }
        }
    }

    /** La demande en route ne sert plus (saisie changée, écran fermé). */
    fun abandonner() {
        demande?.cancel()
        demande = null
        enCours = null
    }

    /** Attend la fin de la demande en route — pour les tests. */
    suspend fun demandeEnCours() {
        demande?.join()
    }

    private fun recevoir(sortie: S, entree: E, numero: Int) {
        // La réponse vaut pour SA saisie, même périmée : y revenir la montrera sans redemander.
        servies[entree] = sortie
        if (jeton == numero) enCours = null
        if (entree == courante) montrer(sortie)
    }

    private fun montrer(sortie: S) {
        if (valeur != sortie) valeur = sortie
    }

    companion object {
        /** ~150 ms après le dernier changement (`DELAI_APERCU_MS` du site, `ApercuSaisie.delai` de l'iOS). */
        const val DELAI_MS = 150L
    }
}
