// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.coprofirst.api.NOTIFICATIONS_SELECTION_PAR_REQUETE
import fr.coprofirst.app.session.ErreurService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

/** Ce que montre l'écran des notifications. */
data class EtatNotifications(
    val liste: List<NotificationRecue> = emptyList(),
    /** Un geste (ou une relecture) qui a échoué : une phrase, et la liste revient à l'état d'avant. */
    val echec: ErreurService? = null,
    /** L'échec d'une sélection dont une partie est déjà supprimée : la liste ne revient qu'en partie. */
    val interrompue: SuppressionInterrompue? = null,
    /** Une relecture tirée du haut de la liste est en cours. */
    val rafraichissement: Boolean = false,
    /** Augmente à chaque geste réussi qui se confirme au toucher (tout lu, sélection supprimée). */
    val confirmations: Int = 0,
    /**
     * Augmente à chaque « Supprimer les lues » réussi : l'avis du site le dit (« Notifications
     * lues supprimées »), avec son retour haptique — comme l'iOS, aucune autre confirmation.
     */
    val luesSupprimees: Int = 0,
) {
    val nonLues: Int get() = liste.count { !it.lue }
    val lues: Int get() = liste.size - nonLues

    /**
     * Les deux sections du site : « Aujourd'hui », ce sont les 24 DERNIÈRES HEURES
     * (`notifications/+page.svelte`, fenêtre glissante), pas le jour du calendrier.
     */
    fun sections(maintenant: Instant = Instant.now()): Pair<List<NotificationRecue>, List<NotificationRecue>> {
        val seuil = maintenant.minus(VINGT_QUATRE_HEURES)
        return liste.partition { !it.creeLe.isBefore(seuil) }
    }

    companion object {
        val VINGT_QUATRE_HEURES: Duration = Duration.ofHours(24)
    }
}

/**
 * Une sélection partie en plusieurs requêtes, dont l'une a échoué APRÈS que le serveur en
 * a supprimé d'autres : ce qui est parti ne revient pas, et la phrase dit combien, puis
 * pourquoi le reste demeure (comme l'iOS, #118).
 */
data class SuppressionInterrompue(
    /** Les identifiants que le serveur a déjà supprimés. */
    val parties: Set<Int>,
    /** Le nombre de notifications choisies. */
    val demandees: Int,
    /** Ce qui a arrêté la requête suivante. */
    val raison: ErreurService,
) : Exception()

/** Ce qu'une suppression s'apprête à retirer : la confirmation le nomme, puis le geste part par SA route. */
sealed interface SuppressionDemandee {
    data class Une(val notification: NotificationRecue) : SuppressionDemandee
    data class Selection(val ids: Set<Int>) : SuppressionDemandee

    /** Toutes les lues du compte ; le nombre est celui de la liste, pour la phrase. */
    data class Lues(val nombre: Int) : SuppressionDemandee
}

/**
 * Les notifications de la résidence ouverte : chargement, liste, gestes — comme
 * `EtatNotifications` de l'iOS. Chaque geste se voit tout de suite ; s'il échoue, la liste
 * revient et une phrase le dit.
 *
 * `noterNonLues` reçoit le nombre de non lues après chaque lecture et chaque geste, réussi ou
 * non : la cloche le montre (`CompteurNonLues`, noté pour la résidence de l'écran).
 */
class ParcoursNotifications(
    private val service: ServiceNotifications,
    private val noterNonLues: (Int) -> Unit = {},
) : ViewModel() {
    private val _etat = MutableStateFlow(EtatNotifications())
    val etat: StateFlow<EtatNotifications> = _etat.asStateFlow()
    private var chargee = false

    /**
     * La première lecture (l'écran attend, et une erreur s'y dit avec « Réessayer ») ; aux
     * apparitions suivantes, la liste déjà lue reste et se relit en fond, comme l'iOS.
     * @throws ErreurService si la première lecture échoue.
     */
    suspend fun charger() {
        if (chargee) {
            viewModelScope.launch { relire() }
            return
        }
        recevoir(service.notifications())
    }

    /** Une liste lue du serveur : elle remplace la liste, efface l'échec, et la cloche suit. */
    private fun recevoir(liste: List<NotificationRecue>) {
        chargee = true
        _etat.update { it.copy(liste = liste, echec = null, interrompue = null) }
        noter()
    }

    private fun noter() = noterNonLues(_etat.value.nonLues)

    /** Tirée du haut de la liste : la relecture se voit, son échec se dit. */
    fun rafraichir() {
        viewModelScope.launch {
            _etat.update { it.copy(rafraichissement = true) }
            relire()
            _etat.update { it.copy(rafraichissement = false) }
        }
    }

    private suspend fun relire() {
        try {
            recevoir(service.notifications())
        } catch (e: ErreurService) {
            _etat.update { it.copy(echec = e, interrompue = null) }
        }
    }

    /** Lance un geste hors de l'écran : il va au bout même si l'écran se ferme. */
    fun lancer(geste: suspend ParcoursNotifications.() -> Unit) {
        viewModelScope.launch { geste() }
    }

    suspend fun marquerLue(notification: NotificationRecue) {
        if (notification.lue) return
        tenter({ liste -> liste.map { if (it.id == notification.id) it.copy(lue = true) else it } }) {
            service.marquerLue(notification.id)
        }
    }

    suspend fun toutMarquerLu() {
        if (tenter({ liste -> liste.map { it.copy(lue = true) } }) { service.toutMarquerLu() }) confirmer()
    }

    suspend fun supprimer(notification: NotificationRecue) {
        tenter({ liste -> liste.filterNot { it.id == notification.id } }) { service.supprimerNotification(notification.id) }
    }

    /**
     * Une sélection — celles choisies, et elles seules : même quand ce sont toutes les lues,
     * la route des lues reste à [supprimerLues], qui en retirerait d'autres (lues depuis,
     * ailleurs). UNE requête tant que le contrat l'accepte ; au-delà de sa borne, des
     * requêtes successives — si l'une échoue, les suivantes ne partent pas. Rend `true`
     * quand TOUT est supprimé.
     */
    suspend fun supprimer(ids: Set<Int>): Boolean {
        if (ids.isEmpty()) return false
        val reussi = tenter({ liste -> liste.filterNot { it.id in ids } }) {
            val parties = mutableSetOf<Int>()
            for (lot in lots(ids)) {
                try {
                    service.supprimerNotifications(lot)
                } catch (e: ErreurService) {
                    if (parties.isEmpty()) throw e
                    throw SuppressionInterrompue(parties.toSet(), ids.size, e)
                }
                parties += lot
            }
        }
        if (reussi) confirmer()
        return reussi
    }

    /**
     * « Supprimer les lues », le geste direct du site : TOUTES les lues du compte, en UNE
     * requête. La liste ne garde que les non lues ; refusée, tout revient. Sans
     * notification lue, aucune requête.
     */
    suspend fun supprimerLues(): Boolean {
        if (_etat.value.lues == 0) return false
        val reussi = tenter({ liste -> liste.filterNot { it.lue } }) { service.supprimerLues() }
        if (reussi) _etat.update { it.copy(luesSupprimees = it.luesSupprimees + 1) }
        return reussi
    }

    /** Le geste confirmé part par SA route. */
    suspend fun supprimer(demande: SuppressionDemandee) {
        when (demande) {
            is SuppressionDemandee.Une -> supprimer(demande.notification)
            is SuppressionDemandee.Selection -> supprimer(demande.ids)
            is SuppressionDemandee.Lues -> supprimerLues()
        }
    }

    private fun confirmer() = _etat.update { it.copy(confirmations = it.confirmations + 1) }

    /**
     * Le geste se voit tout de suite ; s'il échoue, la liste revient et une phrase le dit.
     * Un geste interrompu après un premier succès ne rend que ce que le serveur n'a pas supprimé.
     * Au bout, réussi ou non, la cloche reçoit le nombre qui reste (l'iOS de même).
     */
    private suspend fun tenter(local: (List<NotificationRecue>) -> List<NotificationRecue>, appel: suspend () -> Unit): Boolean {
        val avant = _etat.value.liste
        _etat.update { it.copy(liste = local(it.liste), echec = null, interrompue = null) }
        return try {
            appel()
            true
        } catch (e: SuppressionInterrompue) {
            _etat.update { it.copy(liste = avant.filterNot { n -> n.id in e.parties }, echec = e.raison, interrompue = e) }
            false
        } catch (e: ErreurService) {
            _etat.update { it.copy(liste = avant, echec = e) }
            false
        } finally {
            noter()
        }
    }

    companion object {
        /**
         * Une sélection en lots d'au plus `taille` identifiants, dans l'ordre des
         * identifiants : chacun dans UN lot, et un seul. La taille est la borne du contrat.
         */
        fun lots(ids: Set<Int>, taille: Int = NOTIFICATIONS_SELECTION_PAR_REQUETE): List<Set<Int>> =
            ids.sorted().chunked(taille.coerceAtLeast(1)).map { it.toSet() }
    }
}
