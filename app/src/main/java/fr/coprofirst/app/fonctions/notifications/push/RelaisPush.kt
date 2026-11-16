// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.notifications.push

/**
 * Le drapeau des notifications push d'Android (#23). ÉTEINT : rien ne se propose, rien ne
 * s'enregistre, rien ne part — l'app ne demande pas une autorisation qui ne servirait à rien.
 *
 * Il s'allume quand les trois sont réunis, et pas avant :
 * 1. le relais de CoproFirst sait avertir un appareil Android (FCM), et le contrat publié
 *    accepte son jeton à POST /appareils — ticket du relais dans coprofirst ;
 * 2. Philippe a décidé d'embarquer le SDK de Firebase Messaging (des bibliothèques Google
 *    Play services, sous licence propriétaire, dans une app AGPL) et posé le fichier
 *    google-services — ni l'un ni l'autre n'est dans ce dépôt ;
 * 3. la politique de confidentialité nomme Google et FCM, et la section « Sécurité des
 *    données » de Google Play le déclare.
 *
 * L'allumer, c'est aussi : déclarer `POST_NOTIFICATIONS` au manifeste (sans lui, Android 13
 * refuse la demande d'office), donner un `FournisseurJeton` à `NotificationsPush.de`, et
 * brancher l'ouverture, la déconnexion et la notification touchée (la couche commune).
 */
object RelaisPush {
    const val ACTIF: Boolean = false
}
