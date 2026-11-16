// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.coprofirst.api.modeles.UserRead
import fr.coprofirst.api.routes.AuthApi
import fr.coprofirst.api.routes.NotificationsApi
import fr.coprofirst.app.session.ErreurService
import fr.coprofirst.app.session.SessionOuverte
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Ce que la navigation lit du compte : GET /auth/me, et le nombre de ses alertes non lues. */
interface ServiceCompte {
    suspend fun moi(): UserRead

    /** Compté sur GET /notifications : aucune route ne sert ce nombre seul (l'iOS de même). */
    suspend fun nonLues(): Int
}

class ServiceCompteApi(private val session: SessionOuverte) : ServiceCompte {
    override suspend fun moi(): UserRead = session.appeler { base, client -> AuthApi(base, client).getAuthMe() }

    override suspend fun nonLues(): Int =
        session.appeler { base, client -> NotificationsApi(base, client).getNotifications().count { !it.lue } }
}

/** Ce que montre la barre d'une résidence ouverte. */
data class EtatResidence(
    /** L'identifiant de la page ouverte, ou [PLUS]. */
    val onglet: String,
    /** Les droits du compte ; avant la lecture du profil, aucun outil réservé. */
    val droits: DroitsOnglet = DroitsOnglet(),
    /** Le refus de la mesure d'audience (`opt_out_telemetrie`) ; `null` tant qu'il n'est pas lu. */
    val refusMesure: Boolean? = null,
) {
    companion object {
        const val PLUS = "plus"
    }
}

/**
 * La résidence ouverte : la barre (ses pages, filtrées par les droits du compte), la page
 * choisie — comme `OngletsView` et `Parcours.pagesDeLaBarre` de l'iOS. Le profil se relit
 * à l'ouverture ; un échec laisse la barre sans les outils réservés, et ne bloque rien. Le
 * nombre de non lues de la cloche se relit aussi (`relireNonLues`, dans `compteur`).
 */
class ParcoursResidence(
    val session: SessionOuverte,
    private val compte: ServiceCompte,
    private val compteur: CompteurNonLues = CompteurNonLues.partage,
) : ViewModel() {
    private val pages = session.installation.pages
    private val _etat = MutableStateFlow(EtatResidence(onglet = pages.firstOrNull()?.id ?: EtatResidence.PLUS))
    val etat: StateFlow<EtatResidence> = _etat.asStateFlow()

    init {
        viewModelScope.launch { relireDroits() }
    }

    /**
     * Relit les droits du compte (GET /auth/me) — à l'ouverture, et depuis un écran après un
     * geste qui les change (un rôle qu'on se retire : `ContexteDeMenu.relireDroits`, #87),
     * comme `rafraichirProfil` de l'iOS. La barre et les onglets suivent aussitôt. Un échec
     * garde les droits d'avant ; le serveur tranche de toute façon.
     */
    suspend fun relireDroits() {
        try {
            val moi = compte.moi()
            _etat.update { it.copy(droits = DroitsOnglet.depuis(moi), refusMesure = moi.optOutTelemetrie) }
        } catch (_: ErreurService) {
            // Sans profil : les droits d'avant restent (ceux de tous, à l'ouverture).
        }
    }

    /** Les pages de la barre, pour ces droits : les 4 premières, puis « Plus » pour les autres. */
    fun pagesOuvertes(droits: DroitsOnglet): List<PageDeNavigation> = PagesOuvertes.filtrer(pages, droits)

    fun choisir(onglet: String) = _etat.update { it.copy(onglet = onglet) }

    /** Relit le nombre de non lues de la cloche (l'ouverture de la résidence). */
    suspend fun relireNonLues() = compteur.relire(session.installation.url) { compte.nonLues() }

    companion object {
        /** Le nombre de pages dans la barre ; les suivantes vont dans « Plus » (l'iOS en montre 4). */
        const val VISIBLES = 4
    }
}
