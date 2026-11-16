// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.session.audience

import fr.coprofirst.app.session.ErreurService
import fr.coprofirst.app.session.SessionOuverte
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * La résidence ouverte, telle que la mesure la juge : sa session, et le choix du compte
 * (`opt_out_telemetrie` de GET /auth/me) — `null` tant qu'il n'est pas lu.
 */
data class ResidenceMesuree(val session: SessionOuverte, val refusMesure: Boolean?) {
    val adresse: String get() = session.installation.url
}

/**
 * **La mesure d'audience de l'app** (#29, coprofirst#2726), comme l'iOS (`MesureAudience`) :
 * chaque écran vu se note sous le chemin de l'écran ÉQUIVALENT du site (`CheminDuSite`), et
 * part à la résidence ouverte, en lot, avec le canal de l'app — le tableau de bord du site
 * mêle alors les pages vues par le site et par les apps, et dit d'où elles viennent.
 *
 * Le rythme est celui du site (`front/src/lib/telemetry.ts`) : la file part toutes les
 * 30 s, dès qu'elle atteint la borne du contrat (`LimitesAudience.EVENEMENTS_PAR_LOT`), et au
 * passage en arrière-plan (`viderEnArrierePlan`).
 *
 * Rien ne se note — défaut FERMÉ :
 * - si le compte a refusé la mesure, ou si son choix n'est pas encore lu : on ne collecte pas
 *   ce que le serveur jetterait ;
 * - si l'installation ne dit pas son contrat : rien ne garantit qu'elle connaît le canal
 *   `android` (une installation plus ancienne que le plancher ne s'ouvre pas) ;
 * - sans résidence ouverte, ou en consultation hors ligne : `courante` rend `null`.
 *
 * Aucun identifiant d'appareil, aucun modèle : une page, l'action `view`, le canal. Un échec
 * ne se dit pas et ne se rejoue pas — la mesure ne gêne jamais l'usage.
 *
 * Toutes ses méthodes s'appellent du fil principal ; les envois partent dans `portee`, qui
 * doit survivre à l'écran (celle de l'application).
 */
class MesureAudience(
    private val service: ServiceAudience,
    private val portee: CoroutineScope,
    private val intervalle: Duration = INTERVALLE,
) {
    /** La résidence ouverte, à jour du choix du compte ; `null` : rien à mesurer. */
    var courante: () -> ResidenceMesuree? = { null }

    private val _file = mutableListOf<String>()

    /** Les chemins notés, pas encore partis. */
    val file: List<String> get() = _file.toList()
    private var destinataire: ResidenceMesuree? = null
    private var minuterie: Job? = null

    /** Un écran vu : `chemin` est celui de l'écran équivalent du site ; `null`, rien à noter. */
    fun noter(chemin: String?) {
        val residence = courante() ?: return
        if (!mesurable(residence)) {
            if (destinataire?.adresse == residence.adresse) oublier()
            return
        }
        // Une page vide ou trop longue ferait refuser le lot entier : elle ne s'y met pas.
        if (chemin.isNullOrEmpty() || chemin.codePointCount(0, chemin.length) > LimitesAudience.PAGE) return
        val quittee = destinataire
        if (quittee != null && quittee.adresse != residence.adresse && _file.isNotEmpty()) {
            // Ce qui reste de la résidence quittée part chez elle, pris AVANT la note neuve.
            val restantes = _file.toList()
            _file.clear()
            portee.launch { envoyer(restantes, quittee.session) }
        }
        destinataire = residence
        _file += chemin
        if (_file.size >= LimitesAudience.EVENEMENTS_PAR_LOT) {
            portee.launch { vider() }
        } else {
            armer()
        }
    }

    /**
     * Envoie la file à sa résidence, par lots à la borne du contrat. Le refus posé entre la
     * note et l'envoi est honoré : la file se jette.
     */
    suspend fun vider() {
        minuterie?.cancel()
        minuterie = null
        val cible = destinataire ?: return
        if (_file.isEmpty()) return
        val pages = _file.toList()
        _file.clear()
        val actuelle = courante()
        if (actuelle != null && actuelle.adresse == cible.adresse && !mesurable(actuelle)) return
        envoyer(pages, cible.session)
    }

    /** Le passage en arrière-plan (le `visibilitychange` du site) : la file part. */
    fun viderEnArrierePlan() {
        if (_file.isEmpty()) return
        portee.launch { vider() }
    }

    /**
     * La session est close sans pouvoir rien envoyer — refusée pour de bon, ou fermée après
     * l'envoi de ce qui restait : la file se jette (`Parcours.revenirALaConnexion` de l'iOS,
     * #162). `adresse` : celle de la résidence close — la file d'une autre résidence reste.
     */
    fun oublier(adresse: String? = null) {
        if (adresse != null && destinataire?.adresse != adresse) return
        minuterie?.cancel()
        minuterie = null
        _file.clear()
        destinataire = null
    }

    private suspend fun envoyer(pages: List<String>, session: SessionOuverte) {
        for (lot in pages.chunked(LimitesAudience.EVENEMENTS_PAR_LOT)) {
            try {
                service.envoyer(lot, session)
            } catch (_: ErreurService.SessionExpiree) {
                // La session est refusée pour de bon (#162) : rien d'autre ne part avec elle.
                oublier(session.installation.url)
                return
            } catch (_: ErreurService) {
                // Un échec ne se dit ni ne se rejoue : la mesure ne gêne jamais l'usage.
            }
        }
    }

    private fun armer() {
        if (minuterie != null) return
        minuterie = portee.launch {
            delay(intervalle)
            // La minuterie se retire AVANT d'envoyer : `vider` l'annulerait, et avec elle
            // l'envoi qu'elle porte.
            minuterie = null
            vider()
        }
    }

    companion object {
        /** L'action d'une page vue — celle du site (`trackPageView`). */
        const val ACTION = "view"

        /** La file part au plus tard au bout de ce délai (`FLUSH_INTERVAL` du site). */
        val INTERVALLE: Duration = 30.seconds

        /** L'installation accepte-t-elle la mesure de CE compte, par ce canal ? */
        fun mesurable(residence: ResidenceMesuree): Boolean =
            residence.session.installation.contrat != null && residence.refusMesure == false
    }
}
