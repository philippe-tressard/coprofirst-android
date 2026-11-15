// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.coprofirst.api.modeles.UserRead
import fr.coprofirst.api.routes.AuthApi
import fr.coprofirst.app.session.ErreurService
import fr.coprofirst.app.session.SessionOuverte
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Ce que la navigation lit du compte : GET /auth/me. */
interface ServiceCompte {
    suspend fun moi(): UserRead
}

class ServiceCompteApi(private val session: SessionOuverte) : ServiceCompte {
    override suspend fun moi(): UserRead = session.appeler { base, client -> AuthApi(base, client).getAuthMe() }
}

/** Ce que montre la barre d'une résidence ouverte. */
data class EtatResidence(
    /** L'identifiant de la page ouverte, ou [PLUS]. */
    val onglet: String,
    /** Les droits du compte ; avant la lecture du profil, aucun outil réservé. */
    val droits: DroitsOnglet = DroitsOnglet(),
) {
    companion object {
        const val PLUS = "plus"
    }
}

/**
 * La résidence ouverte : la barre (ses pages, filtrées par les droits du compte), la page
 * choisie — comme `OngletsView` et `Parcours.pagesDeLaBarre` de l'iOS. Le profil se relit
 * à l'ouverture ; un échec laisse la barre sans les outils réservés, et ne bloque rien.
 */
class ParcoursResidence(val session: SessionOuverte, private val compte: ServiceCompte) : ViewModel() {
    private val pages = session.installation.pages
    private val _etat = MutableStateFlow(EtatResidence(onglet = pages.firstOrNull()?.id ?: EtatResidence.PLUS))
    val etat: StateFlow<EtatResidence> = _etat.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val droits = DroitsOnglet.depuis(compte.moi())
                _etat.update { it.copy(droits = droits) }
            } catch (_: ErreurService) {
                // Sans profil : la barre reste celle de tous ; le serveur tranche de toute façon.
            }
        }
    }

    /** Les pages de la barre, pour ces droits : les 4 premières, puis « Plus » pour les autres. */
    fun pagesOuvertes(droits: DroitsOnglet): List<PageDeNavigation> = PagesOuvertes.filtrer(pages, droits)

    fun choisir(onglet: String) = _etat.update { it.copy(onglet = onglet) }

    companion object {
        /** Le nombre de pages dans la barre ; les suivantes vont dans « Plus » (l'iOS en montre 4). */
        const val VISIBLES = 4
    }
}
