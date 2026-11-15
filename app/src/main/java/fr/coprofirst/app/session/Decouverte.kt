// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.session

import fr.coprofirst.api.routes.ConfigApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call

/**
 * La découverte d'une installation : lire ce que sert GET /config à son adresse, et en
 * tirer la résidence — ou la raison pour laquelle on n'y entre pas.
 *
 * @param baseApi la racine de l'API d'une adresse ; les tests la remplacent par celle
 *   d'un serveur local.
 */
class Decouverte(
    private val client: Call.Factory = ClientHttp.partage,
    private val fond: CoroutineDispatcher = Dispatchers.IO,
    private val baseApi: (AdresseInstallation) -> String = { it.baseApi },
) {
    /** @throws ErreurService toujours ce type : chaque échec a sa phrase. */
    suspend fun decouvrir(adresse: AdresseInstallation): Installation = withContext(fond) {
        val cles = try {
            ConfigApi(baseApi(adresse), client).getConfig()
        } catch (erreur: Exception) {
            throw ErreurService.depuisEntree(erreur)
        }
        Installation.depuisConfig(adresse, cles)
    }
}
