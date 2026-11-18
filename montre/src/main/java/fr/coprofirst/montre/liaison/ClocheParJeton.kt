// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.montre.liaison

import fr.coprofirst.api.infrastructure.ClientException
import fr.coprofirst.api.routes.NotificationsApi
import fr.coprofirst.app.fonctions.notifications.NotificationRecue
import fr.coprofirst.app.session.ClientHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.OkHttpClient

/** Le jeton n'ouvre plus rien (retiré, échu, compte changé) : la montre attend le téléphone. */
class JetonRefuse : Exception()

/**
 * La cloche lue par la montre : `GET /notifications`, par le client GÉNÉRÉ et le client HTTP
 * commun (`ClientHttp.partage`), avec le jeton de montre en porteur. C'est la seule route que
 * ce jeton ouvre (coprofirst#2771) ; le contrat (2.6.0 et suivants) la protège par le schéma
 * `JetonAppareil` (http bearer, coprofirst#2824). Le jeton se donne à CETTE instance du client
 * (`accessTokenProvider`), jamais à `ApiClient.accessToken`, global à toutes les requêtes de
 * l'app (#264) ; il ne part que vers la seule adresse de la résidence (`baseApi`).
 */
class ClocheParJeton(
    private val baseApi: String,
    private val jeton: String,
    commun: OkHttpClient = ClientHttp.partage,
    /** La porte des lectures gardées (`GarderLesLectures`), pour le hors-ligne. */
    gardien: Interceptor? = null,
) {
    private val client = gardien?.let { commun.newBuilder().addInterceptor(it).build() } ?: commun

    suspend fun lire(): List<NotificationRecue> = withContext(Dispatchers.IO) {
        try {
            NotificationsApi(baseApi, client).apply { accessTokenProvider = { jeton } }
                .getNotifications().map(NotificationRecue::depuis)
        } catch (refus: ClientException) {
            if (refus.statusCode == 401) throw JetonRefuse() else throw refus
        }
    }
}
