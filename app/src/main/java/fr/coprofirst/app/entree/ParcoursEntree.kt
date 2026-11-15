// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.entree

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.coprofirst.app.session.AdresseInstallation
import fr.coprofirst.app.session.Decouverte
import fr.coprofirst.app.session.ErreurEntree
import fr.coprofirst.app.session.Installation
import fr.coprofirst.app.session.InstallationsConnues
import fr.coprofirst.app.session.StockagePreferences
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Les écrans de l'entrée, dans leur ordre — ceux de l'app iOS (Accueil, Connexion). */
enum class EcranEntree { Accueil, Connexion }

/** Ce que montrent les écrans de l'entrée. */
data class EtatEntree(
    val ecran: EcranEntree = EcranEntree.Accueil,
    /** L'adresse saisie dans « Votre résidence ». */
    val saisie: String = "",
    /** La reconnaissance court. */
    val enCours: Boolean = false,
    /** La résidence reconnue à cette adresse — `null` tant qu'aucune ne l'est. */
    val reconnue: Installation? = null,
    /** Pourquoi la dernière reconnaissance n'a pas abouti. */
    val erreur: ErreurEntree? = null,
)

/**
 * Le parcours d'entrée, sur les choix de l'app iOS (`ConnexionView`) : la résidence se
 * RECONNAÎT pendant la saisie, après une pause de frappe, sans bouton ; à l'ouverture,
 * l'adresse est pré-remplie avec la dernière résidence ouverte. Pas d'annuaire.
 */
class ParcoursEntree(
    private val decouverte: Decouverte,
    private val connues: InstallationsConnues,
) : ViewModel(), ActionsEntree {
    private val _etat = MutableStateFlow(EtatEntree())
    val etat: StateFlow<EtatEntree> = _etat.asStateFlow()
    private var tache: Job? = null

    override fun entrer() {
        val derniere = connues.liste.firstOrNull()?.adresse?.enSaisie
        _etat.update { it.copy(ecran = EcranEntree.Connexion) }
        if (_etat.value.saisie.isEmpty() && derniere != null) saisir(derniere, pause = false)
    }

    /** La démonstration : l'écran de connexion, à son adresse (les personnages : #5). */
    override fun entrerDemo() {
        _etat.update { it.copy(ecran = EcranEntree.Connexion) }
        saisir(AdresseInstallation.DEMO.enSaisie, pause = false)
    }

    override fun saisir(texte: String) = saisir(texte, pause = true)

    override fun retour() {
        tache?.cancel()
        _etat.update { it.copy(ecran = EcranEntree.Accueil, enCours = false) }
    }

    private fun saisir(texte: String, pause: Boolean) {
        val adresse = AdresseInstallation.normaliser(texte)
        val deja = _etat.value.reconnue?.adresse
        _etat.update { it.copy(saisie = texte) }
        if (adresse != null && adresse == deja) return
        tache?.cancel()
        _etat.update { it.copy(reconnue = null, erreur = null, enCours = false) }
        // Une saisie qui n'est pas (encore) une adresse ne dit rien : on attend la suite.
        if (adresse == null) return
        tache = viewModelScope.launch {
            if (pause) delay(PAUSE_DE_FRAPPE_MS)
            _etat.update { it.copy(enCours = true) }
            try {
                val installation = decouverte.decouvrir(adresse)
                connues.retenir(installation)
                _etat.update { it.copy(reconnue = installation, enCours = false) }
            } catch (erreur: ErreurEntree) {
                _etat.update { it.copy(erreur = erreur, enCours = false) }
            }
        }
    }

    companion object {
        /** La pause de frappe avant de reconnaître : celle de l'iOS. */
        const val PAUSE_DE_FRAPPE_MS = 600L

        val Fabrique = viewModelFactory {
            initializer {
                ParcoursEntree(Decouverte(), InstallationsConnues(StockagePreferences(this[APPLICATION_KEY]!!)))
            }
        }
    }
}
