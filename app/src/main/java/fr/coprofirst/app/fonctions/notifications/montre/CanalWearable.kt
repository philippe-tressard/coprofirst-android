// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.notifications.montre

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Le transport réel vers la montre : le Data Layer de Wear OS (`play-services-wearable`,
 * permis par la licence, point 4, #213). Un élément de données au chemin
 * `MessageLiaison.CHEMIN`, que seule une app de même identifiant et de même signature lit ;
 * la montre l'efface dès qu'elle l'a gardé (le jeton ne reste pas dans Play services).
 */
class CanalWearable(contexte: Context) : CanalMontre {
    private val capacites = Wearable.getCapabilityClient(contexte)
    private val donnees = Wearable.getDataClient(contexte)

    override suspend fun montreJointe(): Boolean =
        attendre(capacites.getCapability(MessageLiaison.CAPACITE, CapabilityClient.FILTER_REACHABLE)).nodes.isNotEmpty()

    override suspend fun transmettre(message: MessageLiaison) {
        val requete = PutDataMapRequest.create(MessageLiaison.CHEMIN).apply {
            MessageLiaison.versDonnees(message).forEach { (cle, valeur) -> dataMap.putString(cle, valeur) }
        }
        attendre(donnees.putDataItem(requete.asPutDataRequest().setUrgent()))
    }

    /** Une tâche de Play services, attendue sans la bibliothèque d'adaptation (rien de plus à lier). */
    private suspend fun <T> attendre(tache: Task<T>): T = suspendCancellableCoroutine { suite ->
        tache.addOnCompleteListener { fin ->
            val erreur = fin.exception
            if (erreur != null) suite.resumeWithException(erreur) else suite.resume(fin.result)
        }
    }
}
