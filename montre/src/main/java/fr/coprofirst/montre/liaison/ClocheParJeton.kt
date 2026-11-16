// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.montre.liaison

import fr.coprofirst.api.infrastructure.ClientException
import fr.coprofirst.api.routes.NotificationsApi
import fr.coprofirst.app.fonctions.notifications.NotificationRecue
import fr.coprofirst.app.session.ClientHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

/** Le jeton n'ouvre plus rien (retiré, échu, compte changé) : la montre attend le téléphone. */
class JetonRefuse : Exception()

/**
 * La cloche lue par la montre : `GET /notifications`, par le client GÉNÉRÉ et le client HTTP
 * commun (`ClientHttp.partage`), avec le jeton de montre en porteur. C'est la seule route que
 * ce jeton ouvre (coprofirst#2771). Le porteur ne part que vers l'hôte de la résidence.
 */
class ClocheParJeton(private val baseApi: String, jeton: String, commun: OkHttpClient = ClientHttp.partage) {
    private val hote = baseApi.toHttpUrl().host
    private val client = commun.newBuilder().addInterceptor { chaine ->
        val requete = chaine.request()
        chaine.proceed(
            if (requete.url.host == hote) requete.newBuilder().header("Authorization", "Bearer $jeton").build() else requete,
        )
    }.build()

    suspend fun lire(): List<NotificationRecue> = withContext(Dispatchers.IO) {
        try {
            NotificationsApi(baseApi, client).getNotifications().map(NotificationRecue::depuis)
        } catch (refus: ClientException) {
            if (refus.statusCode == 401) throw JetonRefuse() else throw refus
        }
    }
}
