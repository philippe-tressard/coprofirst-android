// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.reponses

import fr.coprofirst.api.modeles.ReponseCommunauteRead

/**
 * Ce qu'un fil de réponses fait au serveur — l'écran qui le porte le dit (la Communauté relit
 * son onglet, la fiche d'un sondage se recharge), comme l'iOS (`GestesReponses`). `publier`
 * rend `false` sur un échec : le texte saisi reste alors dans le champ (`Reponses.svelte`).
 * `retirer` part APRÈS la confirmation du fil ; `signaler` ouvre la boîte de l'écran
 * (`SignalementAuConseil`).
 */
class GestesReponses(
    val publier: suspend (String) -> Boolean,
    val retirer: suspend (ReponseCommunauteRead) -> Unit,
    val signaler: (ReponseCommunauteRead) -> Unit,
)

/**
 * Les règles d'une réponse (`ReponseCommunauteRead`, le type du contrat — texte brut, jamais
 * du HTML), comme l'iOS (`ReponseCommunaute`, `FilReponses`). Les droits sont SERVIS ou
 * ceux du site, jamais recalculés ici.
 */
object Reponses {
    /** « Supprimer » suit `peut_supprimer` SERVI (coprofirst#2506) ; absent : non. */
    fun peutSupprimer(reponse: ReponseCommunauteRead): Boolean = reponse.peutSupprimer == true

    /**
     * « Signaler » s'offre sur les réponses des AUTRES — la règle du site
     * (`rep.auteur_id !== currentUserId`). Le compte illisible (`moi` absent) laisse
     * « Signaler » partout : le serveur juge.
     */
    fun peutSignaler(reponse: ReponseCommunauteRead, moi: Int?): Boolean = reponse.auteurId != moi

    /** Le conseil syndical : en tête (le serveur trie) et en bleu. */
    fun estCs(reponse: ReponseCommunauteRead): Boolean = reponse.estCs == true

    /** Le nom servi ; vide : `null`, l'écran dit « Un résident ». */
    fun auteur(reponse: ReponseCommunauteRead): String? = reponse.auteurNom.trim().takeIf { it.isNotEmpty() }

    /** Le texte à publier : sans ses blancs ; vide, rien ne part. */
    fun aPublier(saisi: String): String? = saisi.trim().takeIf { it.isNotEmpty() }
}

/** Les repères des tests d'interface, ceux de l'iOS (`reponses.*`, `reponse.<id>`). */
object ReperesReponses {
    const val ENTETE = "reponses.entete"
    const val SAISIE = "reponses.saisie"
    const val ENVOYER = "reponses.envoyer"
    const val CONFIRMER_SUPPRESSION = "reponses.confirmerSuppression"

    fun reponse(id: Int) = "reponse.$id"

    /** Le bouton « ⋮ » des gestes d'une réponse (Android ; l'iOS balaie). */
    fun actions(id: Int) = "reponse.$id.actions"
    fun supprimer(id: Int) = "reponse.$id.supprimer"
    fun signaler(id: Int) = "reponse.$id.signaler"
}
