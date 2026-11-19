// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.notifications.push

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.launch

/**
 * L'app Firebase de CoproFirst (#280) : seulement Cloud Messaging, initialisée à la PREMIÈRE
 * demande de jeton — jamais au lancement (`FirebaseInitProvider` est retiré du manifeste,
 * l'initialisation automatique de Messaging éteinte).
 *
 * Sa configuration (`google-services.json`) n'est PAS dans ce dépôt public : la
 * construction la lit hors du dépôt et la pose en ressources (`google_app_id`,
 * `google_api_key`, `gcm_defaultSenderId`, `project_id` — les noms que lit
 * `FirebaseOptions.fromResource`), voir `app/build.gradle.kts`. Une construction sans elle
 * (la CI, un contributeur) n'a pas de push : `fournisseur` rend `null`.
 */
object ConfigFirebase {
    /** Le fournisseur de jeton, ou `null` sans configuration. */
    fun fournisseur(contexte: Context): FournisseurJeton? {
        val app = contexte.applicationContext
        FirebaseOptions.fromResource(app) ?: return null
        return FournisseurFcm({ messagerie(app) }) { jeton ->
            NotificationsPush.portee.launch { NotificationsPush.de(app).jetonRecu(jeton) }
        }
    }

    /** L'app Firebase par défaut, initialisée une fois ; `null` sans configuration. */
    fun initialiser(contexte: Context): FirebaseApp? {
        val app = contexte.applicationContext
        FirebaseApp.getApps(app).firstOrNull()?.let { return it }
        val options = FirebaseOptions.fromResource(app) ?: return null
        return FirebaseApp.initializeApp(app, options)
    }

    private fun messagerie(contexte: Context): FirebaseMessaging? =
        initialiser(contexte)?.let { FirebaseMessaging.getInstance() }
}

/**
 * Le jeton FCM, demandé à Firebase Messaging : il arrive à `recu`. Un échec (pas de Google
 * Play services, pas de réseau) ne se dit pas et ne se journalise pas : la cloche reste le
 * chemin de l'information, et la prochaine ouverture redemande.
 */
class FournisseurFcm(
    private val messagerie: () -> FirebaseMessaging?,
    private val recu: (JetonPush) -> Unit,
) : FournisseurJeton {
    override fun demander() {
        messagerie()?.token?.addOnSuccessListener { valeur -> if (!valeur.isNullOrBlank()) recu(JetonPush(valeur)) }
    }
}
