// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.notifications.push

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import fr.coprofirst.app.commun.ReglagesAppareil

/** Où en est l'autorisation des notifications, comme l'iOS (`AutorisationPush`). */
enum class AutorisationPush {
    /** Pas encore lue. */
    INCONNUE,

    /** Jamais demandée par l'app : elle peut la proposer — au bon moment, jamais à froid. */
    NON_DEMANDEE,
    ACCORDEE,

    /** Refusée : seuls les réglages du système la rendent ; l'app ne la redemande pas. */
    REFUSEE,
}

/**
 * Ce qui demande au service de notifications le jeton de l'app ; il le remet à
 * `NotificationsPush.jetonRecu`. Celui de l'app : `FournisseurFcm` (Firebase Cloud
 * Messaging, #280) ; aucun quand la construction n'a pas reçu la configuration Firebase.
 */
fun interface FournisseurJeton {
    fun demander()
}

/** Ce que l'app demande au système pour les notifications, comme l'iOS (`CentreNotifications`). */
interface CentreNotifications {
    fun autorisation(): AutorisationPush

    /** Le système a répondu à la demande d'autorisation (ou il n'y en avait pas à faire, avant Android 13). */
    fun demandeFaite()

    /** Demande le jeton ; il arrive par `NotificationsPush.jetonRecu`. */
    fun inscrire()
}

/**
 * Le centre d'Android. La demande est NOTÉE par l'app : avant Android 13, le système
 * autorise d'office, et l'app ne s'enregistrerait jamais sans un « Activer » — comme
 * l'iOS, la personne choisit d'abord, dans la cloche.
 */
class CentreAndroid(
    private val contexte: Context,
    private val reglages: ReglagesAppareil,
    private val fournisseur: FournisseurJeton?,
) : CentreNotifications {
    override fun autorisation(): AutorisationPush = when {
        reglages.lire(CLE_DEMANDE) == null -> AutorisationPush.NON_DEMANDEE
        NotificationManagerCompat.from(contexte).areNotificationsEnabled() -> AutorisationPush.ACCORDEE
        else -> AutorisationPush.REFUSEE
    }

    override fun demandeFaite() = reglages.ecrire(CLE_DEMANDE, "oui")

    override fun inscrire() {
        fournisseur?.demander()
    }

    private companion object {
        const val CLE_DEMANDE = "autorisation-demandee"
    }
}
