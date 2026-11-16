// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.montre.liaison

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import fr.coprofirst.app.fonctions.notifications.montre.LiaisonMontre
import fr.coprofirst.app.fonctions.notifications.montre.MessageLiaison
import fr.coprofirst.app.session.ChiffrementKeystore

/**
 * Ce que le téléphone transmet par le Data Layer (`play-services-wearable`, permis par la
 * licence, #213) : la liaison, ou son effacement. La montre la garde chiffrée
 * (`CoffrePreferences`), puis EFFACE l'élément de données : le jeton ne reste pas dans Google
 * Play services. Seule une app de même identifiant et de même signature écrit à ce chemin.
 */
class ReceptionLiaison : WearableListenerService() {
    override fun onDataChanged(evenements: DataEventBuffer) {
        if (!LiaisonMontre.ACTIF) return
        val coffre = CoffrePreferences(this, ChiffrementKeystore(CoffrePreferences.ALIAS))
        for (evenement in evenements) {
            val element = evenement.dataItem
            if (evenement.type != DataEvent.TYPE_CHANGED || element.uri.path != MessageLiaison.CHEMIN) continue
            val donnees = DataMapItem.fromDataItem(element).dataMap
            MessageLiaison.depuis(donnees.keySet().associateWith { donnees.getString(it) })?.let { Liaisons.recevoir(it, coffre) }
            Wearable.getDataClient(this).deleteDataItems(element.uri)
        }
    }
}
