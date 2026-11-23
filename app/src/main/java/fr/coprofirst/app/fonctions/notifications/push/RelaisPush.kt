// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.notifications.push

/**
 * Le drapeau des notifications push d'Android (#280). ÉTEINT : rien ne se propose, rien ne
 * s'enregistre, rien ne part, Firebase ne s'initialise pas — l'app ne demande pas une
 * autorisation qui ne servirait à rien.
 *
 * Ce qui est déjà réuni (voie A, décision de Philippe du 18/11/2026) : le relais de
 * CoproFirst avertit un téléphone Android par FCM (coprofirst#2789, contrat 2.10.0, en
 * production depuis v3.119.0) ; la licence permet Firebase Messaging (point 4) ; la
 * politique de confidentialité nomme Google et FCM ; la section « Sécurité des données »
 * le déclare (`docs/play-store/securite-des-donnees.md`). Le code est branché : l'offre de
 * la cloche, l'enregistrement par résidence, le retrait à la déconnexion, la notification
 * touchée.
 *
 * Il s'allume quand Philippe a allumé le relais — le compte de service FCM posé sur la
 * référence, le service `notifications_push` ouvert —, et pas avant. Même allumé, rien ne
 * se fait sans la configuration Firebase injectée à la construction (`ConfigFirebase`), ni
 * auprès d'une résidence qui ne dit pas servir le plancher (`Installation.sertLePlancher`).
 *
 * ⚠️ Réserve à relire le jour où il s'allume (#311, comme l'iOS) : le relais FCM part du
 * plancher, contrat 2.10.0 — que v3.119.0 sert déjà, SANS le filtre « seule l'alerte urgente
 * part vers un téléphone », arrivé en v3.120.0. Une installation en v3.119.0 ferait sonner
 * toute la cloche. Aucune installation connue ne la sert (23/11/2026 : toutes en v3.120.0 ou
 * plus).
 */
object RelaisPush {
    const val ACTIF: Boolean = false
}
