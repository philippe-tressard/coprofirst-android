// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.fil

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.coprofirst.api.routes.FluxApi
import fr.coprofirst.app.commun.fil.Chronologie
import fr.coprofirst.app.commun.fil.ElementDuFil
import fr.coprofirst.app.commun.fil.JourDuFil
import fr.coprofirst.app.session.ErreurService
import fr.coprofirst.app.session.SessionOuverte
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Ce que le fil demande à la résidence ouverte, comme l'iOS (`ServiceAPI.fil`). */
interface ServiceFil {
    /** GET /flux : le flux que lit le tableau de bord du site. */
    suspend fun fil(): List<ElementDuFil>

    /**
     * GET /flux?du=&au= : le fil d'une PÉRIODE (coprofirst#2834, servi au plancher), chaque carte
     * à son jour servi (`ElementDuFil.jour`) — la semaine du carnet (#286).
     */
    suspend fun periode(du: LocalDate, au: LocalDate): List<ElementDuFil>
}

class ServiceFilApi(private val session: SessionOuverte) : ServiceFil {
    override suspend fun fil(): List<ElementDuFil> =
        session.appeler { base, client -> FluxApi(base, client).getFlux() }.items.orEmpty().map(ElementDuFil::depuis)

    override suspend fun periode(du: LocalDate, au: LocalDate): List<ElementDuFil> =
        session.appeler { base, client -> FluxApi(base, client).getFlux(du = du, au = au) }.items.orEmpty().map(ElementDuFil::depuis)
}

/** Ce que montre le fil. */
data class EtatFil(
    val elements: List<ElementDuFil> = emptyList(),
    /** Une relecture qui a échoué : une phrase, et le fil déjà lu reste. */
    val echec: ErreurService? = null,
    /** Une relecture tirée du haut de la liste est en cours. */
    val rafraichissement: Boolean = false,
) {
    val jours: List<JourDuFil> get() = Chronologie.parJour(elements)
}

/**
 * Le fil de la résidence ouverte : la première lecture (l'écran attend, une erreur s'y dit
 * avec « Réessayer ») ; aux apparitions suivantes, le fil déjà lu reste et se relit en fond,
 * comme l'iOS (`.task`, `.refreshable`).
 */
class ParcoursFil(private val service: ServiceFil) : ViewModel() {
    private val _etat = MutableStateFlow(EtatFil())
    val etat: StateFlow<EtatFil> = _etat.asStateFlow()
    private var lu = false

    /** @throws ErreurService si la première lecture échoue. */
    suspend fun charger() {
        if (lu) {
            viewModelScope.launch { relire() }
            return
        }
        val elements = service.fil()
        lu = true
        _etat.update { it.copy(elements = elements, echec = null) }
    }

    /**
     * La semaine du carnet (#286), du lundi au dimanche : lue à chaque ouverture, jamais gardée
     * (le fil du jour reste celui de `charger`). @throws ErreurService si la lecture échoue.
     */
    suspend fun semaine(lundi: LocalDate): List<ElementDuFil> = service.periode(lundi, lundi.plusDays(6))

    /** Tiré du haut de la liste : la relecture se voit, son échec se dit. */
    fun rafraichir() {
        viewModelScope.launch {
            _etat.update { it.copy(rafraichissement = true) }
            relire()
            _etat.update { it.copy(rafraichissement = false) }
        }
    }

    private suspend fun relire() {
        try {
            val elements = service.fil()
            lu = true
            _etat.update { it.copy(elements = elements, echec = null) }
        } catch (e: ErreurService) {
            _etat.update { it.copy(echec = e) }
        }
    }
}
