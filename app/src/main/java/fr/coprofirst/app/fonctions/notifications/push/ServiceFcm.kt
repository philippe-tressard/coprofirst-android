// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.notifications.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import fr.coprofirst.app.navigation.CompteurNonLues
import kotlinx.coroutines.launch

/**
 * Ce que Firebase Messaging remet à l'app (#280). La charge ne porte que le texte fixe du
 * relais et l'abonnement opaque (`data.a`, spec push §5 et §11) : rien de la résidence, rien
 * de l'alerte. App fermée ou en arrière-plan, Android montre lui-même l'avertissement (canal
 * `AvertissementPush.CANAL`) et le toucher ouvre l'app avec l'abonnement
 * (`ActiviteAccueil`) ; app ouverte, `onMessageReceived` décide (`NotificationsPush.arrivee`).
 * Rien ne se journalise.
 */
class ServiceFcm : FirebaseMessagingService() {
    override fun onCreate() {
        // Firebase n'est jamais initialisé au lancement : un message peut réveiller l'app avant.
        ConfigFirebase.initialiser(this)
        super.onCreate()
    }

    override fun onNewToken(token: String) {
        val push = NotificationsPush.de(this)
        if (!push.relaisActif) return
        NotificationsPush.portee.launch { push.jetonRecu(JetonPush(token)) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val push = NotificationsPush.de(this)
        if (!push.relaisActif) return
        val abonnement = message.data[NotificationsPush.CLE_ABONNEMENT] ?: return
        val arrivee = push.arrivee(abonnement)
        if (arrivee is ArriveePush.ResidenceOuverte) CompteurNonLues.partage.demanderRelecture(arrivee.residence)
        if (arrivee != ArriveePush.Ignoree) {
            AvertissementPush.montrer(
                this, abonnement, message.notification?.title, message.notification?.body,
                silencieux = arrivee is ArriveePush.ResidenceOuverte,
            )
        }
    }
}
