// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.session

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Le client HTTP de l'app, en un seul endroit : c'est ici que se règle ce qui protège
 * chaque échange, et aucun écran n'en fabrique un autre.
 *
 * - Aucune redirection suivie : une installation sert son API à son adresse ; suivre
 *   une redirection mènerait le client vers un hôte que l'utilisateur n'a pas donné
 *   (et, pour `followSslRedirects`, de HTTPS vers HTTP).
 * - Le clair est refusé par le manifeste (`usesCleartextTraffic`).
 */
object ClientHttp {
    val partage: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}
