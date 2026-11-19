// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.notifications.push

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import fr.coprofirst.app.ActiviteAccueil
import fr.coprofirst.app.R
import fr.coprofirst.app.partage.R as RCommun

/**
 * L'avertissement sur le téléphone (#280) : un canal, « Alertes de la résidence », qui SONNE
 * (importance haute) — seule l'alerte d'une actualité ⚡ urgente du conseil syndical part
 * vers un téléphone (coprofirst v3.120.0, spec push §12). Le texte est celui, fixe, du
 * relais ; l'icône, le logo de CoproFirst que le système teinte (comme la complication de
 * la montre). Le toucher ouvre l'app avec l'abonnement, et l'app la cloche de sa résidence.
 * Un avertissement par résidence : le suivant remplace le précédent (étiquette =
 * l'abonnement, comme `collapse_key`).
 */
object AvertissementPush {
    /** Le canal, nommé aussi au manifeste (`default_notification_channel_id`) pour l'app en arrière-plan. */
    const val CANAL = "alertes"

    /** Crée le canal (sans effet s'il existe) : avant la demande d'autorisation, et avant tout avertissement. */
    fun creerCanal(contexte: Context) {
        val canal = NotificationChannelCompat.Builder(CANAL, NotificationManagerCompat.IMPORTANCE_HIGH)
            .setName(contexte.getString(R.string.push_canal))
            .setDescription(contexte.getString(R.string.push_canal_description))
            .build()
        NotificationManagerCompat.from(contexte).createNotificationChannel(canal)
    }

    /** L'intention du toucher : l'app, et l'abonnement — rien d'autre. */
    fun ouverture(contexte: Context, abonnement: String): Intent =
        Intent(contexte, ActiviteAccueil::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(NotificationsPush.CLE_ABONNEMENT, abonnement)

    /**
     * Montre l'avertissement d'une notification arrivée app ouverte. `silencieux` : la
     * résidence est à l'écran — rangé sans bruit, comme le `.list` de l'iOS. Sans
     * autorisation, rien ne se montre (le système le refuserait).
     */
    @SuppressLint("MissingPermission") // l'autorisation se lit juste avant (areNotificationsEnabled)
    fun montrer(contexte: Context, abonnement: String, titre: String?, texte: String?, silencieux: Boolean) {
        val centre = NotificationManagerCompat.from(contexte)
        if (!centre.areNotificationsEnabled()) return
        creerCanal(contexte)
        val toucher = PendingIntent.getActivity(
            contexte, abonnement.hashCode(), ouverture(contexte, abonnement),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val avertissement = NotificationCompat.Builder(contexte, CANAL)
            .setSmallIcon(RCommun.drawable.logo)
            .setContentTitle(titre ?: contexte.getString(R.string.nom_app))
            .setContentText(texte ?: contexte.getString(R.string.push_texte))
            .setContentIntent(toucher)
            .setAutoCancel(true)
            .setSilent(silencieux)
            .build()
        centre.notify(abonnement, ID, avertissement)
    }

    private const val ID = 1
}
