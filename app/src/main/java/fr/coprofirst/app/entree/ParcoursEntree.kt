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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Les écrans de l'entrée, dans leur ordre. */
sealed interface EcranEntree {
    val rang: Int

    data object Accueil : EcranEntree { override val rang = 0 }
    data object Adresse : EcranEntree { override val rang = 1 }
    data class Residence(val installation: Installation) : EcranEntree { override val rang = 2 }
}

/** Ce que montrent les écrans de l'entrée. */
data class EtatEntree(
    val ecran: EcranEntree = EcranEntree.Accueil,
    /** La saisie de l'adresse, gardée quand on revient sur l'écran. */
    val saisie: String = "",
    /** L'adresse dont la découverte court — `null` au repos. */
    val enCours: AdresseInstallation? = null,
    /** Pourquoi la dernière découverte n'a pas abouti. */
    val erreur: ErreurEntree? = null,
    val connues: List<Installation> = emptyList(),
)

/** Le parcours d'entrée : de l'accueil à une résidence reconnue. */
class ParcoursEntree(
    private val decouverte: Decouverte,
    private val connues: InstallationsConnues,
) : ViewModel(), ActionsEntree {
    private val _etat = MutableStateFlow(EtatEntree(connues = connues.liste))
    val etat: StateFlow<EtatEntree> = _etat.asStateFlow()
    private var tache: Job? = null

    override fun saisir(texte: String) = _etat.update { it.copy(saisie = texte, erreur = null) }

    override fun ouvrirAdresse() = aller(EcranEntree.Adresse)

    /** Retour d'un écran : vers l'accueil, en abandonnant une découverte en cours. */
    override fun retour() = aller(EcranEntree.Accueil)

    override fun entrerSaisie() {
        val adresse = AdresseInstallation.normaliser(_etat.value.saisie)
        if (adresse == null) {
            _etat.update { it.copy(erreur = ErreurEntree.AdresseInvalide) }
        } else {
            decouvrir(adresse)
        }
    }

    override fun entrerDemo() = decouvrir(AdresseInstallation.DEMO)

    /** Rouvre une résidence connue : elle se redécouvre, son contrat a pu changer. */
    override fun rouvrir(installation: Installation) {
        installation.adresse?.let(::decouvrir)
    }

    override fun oublier(installation: Installation) {
        connues.oublier(installation)
        _etat.update { it.copy(ecran = EcranEntree.Accueil, connues = connues.liste) }
    }

    private fun decouvrir(adresse: AdresseInstallation) {
        if (_etat.value.enCours == adresse) return
        tache?.cancel()
        _etat.update { it.copy(enCours = adresse, erreur = null) }
        tache = viewModelScope.launch {
            try {
                val installation = decouverte.decouvrir(adresse)
                connues.retenir(installation)
                _etat.update {
                    it.copy(ecran = EcranEntree.Residence(installation), enCours = null, connues = connues.liste)
                }
            } catch (erreur: ErreurEntree) {
                _etat.update { it.copy(enCours = null, erreur = erreur) }
            }
        }
    }

    private fun aller(ecran: EcranEntree) {
        tache?.cancel()
        _etat.update { it.copy(ecran = ecran, enCours = null, erreur = null) }
    }

    companion object {
        val Fabrique = viewModelFactory {
            initializer {
                ParcoursEntree(Decouverte(), InstallationsConnues(StockagePreferences(this[APPLICATION_KEY]!!)))
            }
        }
    }
}
