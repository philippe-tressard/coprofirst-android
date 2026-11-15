// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.entree

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.coprofirst.app.commun.GestesDeSortie
import fr.coprofirst.app.commun.pieces.PiecesLocales
import fr.coprofirst.app.session.AdresseInstallation
import fr.coprofirst.app.session.Connexion
import fr.coprofirst.app.session.Decouverte
import fr.coprofirst.app.session.ErreurService
import fr.coprofirst.app.session.GardeHote
import fr.coprofirst.app.session.InstallationsConnues
import fr.coprofirst.app.session.OrigineAdresse
import fr.coprofirst.app.session.PreuveHote
import fr.coprofirst.app.session.SessionOuverte
import fr.coprofirst.app.session.SessionsParHote
import fr.coprofirst.app.session.StockagePreferences
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Le parcours d'entrée, sur les choix de l'app iOS (`ConnexionView`, `GardeHote`) : la
 * résidence se RECONNAÎT pendant la saisie, après une pause de frappe ; « Se connecter »
 * passe par la garde de l'hôte ; la résidence n'est retenue qu'une fois une connexion
 * réussie, et pré-remplit alors l'écran suivant. Pas d'annuaire.
 */
class ParcoursEntree(
    private val decouverte: Decouverte,
    private val sessions: SessionsParHote,
    private val connexion: Connexion = Connexion(sessions),
    private val connues: InstallationsConnues,
    /** Efface ce que les pièces ouvertes laissent sur l'appareil (`PiecesLocales.effacer`). */
    private val effacerLesPieces: () -> Unit = {},
    /** La racine de l'API d'une adresse, pour la session ouverte ; les tests la remplacent par un serveur local. */
    private val baseApi: (AdresseInstallation) -> String = { it.baseApi },
    private val lirePreuve: suspend (AdresseInstallation) -> PreuveHote = { PreuveHote.lire(it) },
) : ViewModel(), ActionsEntree, GestesDeSortie {
    private val _etat = MutableStateFlow(EtatEntree())
    val etat: StateFlow<EtatEntree> = _etat.asStateFlow()
    private val garde = GardeHote { connues.liste }
    private var reconnaissance: Job? = null

    override fun entrer() {
        val derniere = connues.liste.firstOrNull()?.adresse?.enSaisie
        _etat.update { it.copy(ecran = EcranEntree.Connexion) }
        if (_etat.value.saisie.isEmpty() && derniere != null) saisir(derniere, pause = false)
    }

    /** La démonstration : l'écran de connexion, à son adresse (les personnages : #10). */
    override fun entrerDemo() {
        _etat.update { it.copy(ecran = EcranEntree.Connexion) }
        saisir(AdresseInstallation.DEMO.enSaisie, pause = false)
    }

    override fun saisir(texte: String) = saisir(texte, pause = true)

    override fun saisirEmail(texte: String) = _etat.update { it.copy(email = texte, erreurConnexion = null) }

    override fun saisirMotDePasse(texte: String) = _etat.update { it.copy(motDePasse = texte, erreurConnexion = null) }

    override fun basculerVoirMotDePasse() = _etat.update { it.copy(voirMotDePasse = !it.voirMotDePasse) }

    override fun retour() {
        reconnaissance?.cancel()
        _etat.update { it.copy(ecran = EcranEntree.Accueil, enCours = false, confirmation = null) }
    }

    /** Le seul chemin du mot de passe : un hôte à confirmer ouvre la confirmation, RIEN ne part. */
    override fun seConnecter() {
        val etat = _etat.value
        val installation = etat.reconnue ?: return
        val adresse = installation.adresse ?: return
        if (!etat.connexionPossible) return
        if (garde.exigeConfirmation(adresse, OrigineAdresse.Saisie)) {
            _etat.update { it.copy(confirmation = ConfirmationHote(installation)) }
            viewModelScope.launch {
                val preuve = lirePreuve(adresse)
                _etat.update { e -> e.copy(confirmation = e.confirmation?.takeIf { it.installation == installation }?.copy(preuve = preuve)) }
            }
            return
        }
        _etat.update { it.copy(connexionEnCours = true, erreurConnexion = null) }
        viewModelScope.launch {
            try {
                connexion.connecter(installation, etat.email, etat.motDePasse)
                connues.retenir(installation)
                _etat.update { it.copy(connexionEnCours = false, motDePasse = "", ouverte = installation, ecran = EcranEntree.Espace) }
            } catch (erreur: ErreurService) {
                _etat.update { it.copy(connexionEnCours = false, erreurConnexion = erreur) }
            }
        }
    }

    override fun basculerSecondAccord() = _etat.update { e ->
        e.copy(confirmation = e.confirmation?.let { it.copy(secondAccord = !it.secondAccord) })
    }

    override fun confirmerHote() {
        val confirmation = _etat.value.confirmation ?: return
        val adresse = confirmation.installation.adresse ?: return
        if (!garde.confirmer(adresse, confirmation.preuve, confirmation.secondAccord)) return
        _etat.update { it.copy(confirmation = null) }
        seConnecter()
    }

    override fun renoncerHote() = _etat.update { it.copy(confirmation = null) }

    /** La session de la résidence ouverte, pour les écrans de ses menus — `null` hors session. */
    fun sessionOuverte(): SessionOuverte? = _etat.value.ouverte?.let { SessionOuverte(it, sessions, baseApi = baseApi) }

    override fun deconnecter() {
        val ouverte = _etat.value.ouverte ?: return
        _etat.update { it.copy(ouverte = null, ecran = EcranEntree.Connexion) }
        effacerLesPieces()
        viewModelScope.launch { connexion.deconnecter(ouverte) }
    }

    /** Comme `Parcours.oublierTout` de l'iOS : chaque session fermée, rien de retenu, l'accueil. */
    override fun oublierTout() {
        val ouverte = _etat.value.ouverte
        reconnaissance?.cancel()
        _etat.value = EtatEntree()
        connues.vider()
        effacerLesPieces()
        viewModelScope.launch {
            if (ouverte != null) connexion.deconnecter(ouverte)
            sessions.oublierTout()
        }
    }

    private fun saisir(texte: String, pause: Boolean) {
        val adresse = AdresseInstallation.normaliser(texte)
        val deja = _etat.value.reconnue?.adresse
        _etat.update { it.copy(saisie = texte) }
        if (adresse != null && adresse == deja) return
        reconnaissance?.cancel()
        _etat.update { it.copy(reconnue = null, erreur = null, enCours = false, erreurConnexion = null) }
        // Une saisie qui n'est pas (encore) une adresse ne dit rien : on attend la suite.
        if (adresse == null) return
        reconnaissance = viewModelScope.launch {
            if (pause) delay(PAUSE_DE_FRAPPE_MS)
            _etat.update { it.copy(enCours = true) }
            try {
                val installation = decouverte.decouvrir(adresse)
                _etat.update { it.copy(reconnue = installation, enCours = false) }
            } catch (erreur: ErreurService) {
                _etat.update { it.copy(erreur = erreur, enCours = false) }
            }
        }
    }

    companion object {
        /** La pause de frappe avant de reconnaître : celle de l'iOS. */
        const val PAUSE_DE_FRAPPE_MS = 600L

        val Fabrique = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY]!!
                val stockage = StockagePreferences(application)
                // UNE session HTTP par hôte pour toute l'app : la connexion y pose ses
                // cookies, les écrans des menus les y relisent.
                val sessions = SessionsParHote()
                ParcoursEntree(
                    Decouverte(), sessions, Connexion(sessions), InstallationsConnues(stockage),
                    effacerLesPieces = { PiecesLocales.effacer(application) },
                )
            }
        }
    }
}
