// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.montre

/**
 * Le drapeau de la liaison téléphone ↔ montre (#207). ÉTEINT : la montre ne se relie pas,
 * ne garde rien, n'appelle rien — elle dit seulement de l'ouvrir par le téléphone.
 *
 * Il s'allume quand les trois sont réunis, et pas avant :
 * 1. coprofirst sert le jeton de montre (coprofirst#2771 : portée « montre », `GET
 *    /notifications` seul, révocable, expirant, émis au téléphone authentifié) et le
 *    contrat publié au plancher le décrit ;
 * 2. Philippe a tranché le canal téléphone ↔ montre (#207, verrou 1) — sur Android, il
 *    n'existe que par une bibliothèque Google Play services propriétaire ;
 * 3. la section « Sécurité des données » de Google Play et la politique de confidentialité
 *    disent ce que la montre reçoit et lit.
 *
 * Comme `RelaisPush` côté téléphone.
 */
object LiaisonMontre {
    const val ACTIF: Boolean = false
}
