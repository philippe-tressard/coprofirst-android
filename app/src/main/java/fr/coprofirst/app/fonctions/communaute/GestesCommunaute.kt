// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.communaute

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalResources
import fr.coprofirst.app.commun.AvisDuGeste
import fr.coprofirst.app.commun.TonAvis
import fr.coprofirst.app.commun.phrase
import fr.coprofirst.app.session.ErreurService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Un geste de la Communauté, comme `EtatCommunaute.agir` de l'iOS : la phrase du serveur
 * (ou celle du site) s'il réussit, celle de l'erreur sinon ; puis la liste se relit — elle
 * dit ce que le serveur a retenu.
 */
class GestesCommunaute(private val avis: AvisDuGeste, private val ressources: Resources, private val portee: CoroutineScope) {
    /**
     * `geste` rend la phrase du serveur, ou `null` pour dire `reussite`. `apres(reussi)` suit
     * dans tous les cas : relire, ou défaire ce qui se voyait d'avance.
     */
    fun agir(@StringRes reussite: Int, apres: (reussi: Boolean) -> Unit = {}, geste: suspend () -> String?) {
        portee.launch { apres(tenter(reussite, geste)) }
    }

    /** Le même, attendu : rend `true` si le serveur l'a retenu (le fil garde sinon la saisie). */
    suspend fun tenter(@StringRes reussite: Int, geste: suspend () -> String?): Boolean = try {
        avis.dire(geste() ?: ressources.getString(reussite))
        true
    } catch (e: ErreurService) {
        avis.dire(e.phrase(ressources), TonAvis.Erreur)
        false
    }
}

@Composable
fun rememberGestes(avis: AvisDuGeste): GestesCommunaute {
    val ressources = LocalResources.current
    val portee = rememberCoroutineScope()
    return remember(avis, ressources, portee) { GestesCommunaute(avis, ressources, portee) }
}
