// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.session

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** L'appareil est-il en ligne ? Un seul observateur pour toute l'app, comme l'iOS (`Reseau`). */
interface Reseau {
    val enLigne: StateFlow<Boolean>

    /** Un état qui ne change pas — ou que le test change : `etat.value = false`. */
    class Fige(enLigne: Boolean = true) : Reseau {
        val etat = MutableStateFlow(enLigne)
        override val enLigne: StateFlow<Boolean> = etat.asStateFlow()
    }
}

/**
 * L'observateur du système (`ConnectivityManager`, réseau par défaut validé). Demande la
 * permission ordinaire `ACCESS_NETWORK_STATE`, accordée à l'installation, sans invite.
 */
class ReseauDuSysteme(contexte: Context) : Reseau {
    private val etat = MutableStateFlow(true)
    override val enLigne: StateFlow<Boolean> = etat.asStateFlow()

    init {
        val connectivite = contexte.getSystemService(ConnectivityManager::class.java)
        if (connectivite != null) {
            etat.value = connectivite.activeNetwork?.let(connectivite::getNetworkCapabilities)?.let(::valide) ?: false
            connectivite.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(reseau: Network, capacites: NetworkCapabilities) {
                    etat.value = valide(capacites)
                }

                override fun onLost(reseau: Network) {
                    etat.value = false
                }
            })
        }
    }

    private fun valide(capacites: NetworkCapabilities) =
        capacites.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capacites.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
