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
import android.os.Build
import fr.coprofirst.app.session.AdresseInstallation
import fr.coprofirst.app.session.CoffreKeystore
import fr.coprofirst.app.session.Connexion
import fr.coprofirst.app.session.Decouverte
import fr.coprofirst.app.session.Deverrouillage
import fr.coprofirst.app.session.ErreurService
import fr.coprofirst.app.session.GardeHote
import fr.coprofirst.app.session.Installation
import fr.coprofirst.app.session.InstallationsConnues
import fr.coprofirst.app.session.OrigineAdresse
import fr.coprofirst.app.session.PreuveHote
import fr.coprofirst.app.session.RepriseAnnulee
import fr.coprofirst.app.session.ReprisePerdue
import fr.coprofirst.app.session.SessionOuverte
import fr.coprofirst.app.session.SessionsGardees
import fr.coprofirst.app.session.SessionsGardeesKeystore
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
 * réussie, et pré-remplit alors l'écran suivant, courriel compris. Pas d'annuaire.
 *
 * Au lancement, comme l'iOS (`EntreeView`) : l'accueil à un appareil qui n'a encore ouvert
 * aucune résidence ; ensuite la dernière — l'écran de retour si sa session est gardée
 * (la biométrie, puis le fil), sinon la connexion pré-remplie.
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
    /** Une seule demande d'office au retour : revenu du mot de passe, l'utilisateur choisit. */
    private var repriseTentee = false

    /**
     * Ce qui demande la biométrie (ou le code) : posé par l'écran, qui tient l'activité,
     * retiré quand il la quitte. Sans lui, aucune session gardée ne se rouvre.
     */
    var deverrouillage: Deverrouillage? = null

    init {
        connues.derniere?.let { derniere ->
            if (connexion.aUneSessionGardee(derniere)) {
                _etat.update { it.copy(ecran = EcranEntree.Retour, reconnue = derniere) }
            } else {
                _etat.update { it.copy(ecran = EcranEntree.Connexion, parMotDePasse = true) }
                derniere.adresse?.let { saisir(it.enSaisie, pause = false) }
            }
        }
    }

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

    /** Le retour de la connexion : à l'écran de retour si la dernière session est gardée, sinon l'accueil. */
    override fun retour() {
        reconnaissance?.cancel()
        val derniere = connues.derniere?.takeIf(connexion::aUneSessionGardee)
        _etat.update {
            it.copy(
                ecran = if (derniere != null) EcranEntree.Retour else EcranEntree.Accueil,
                reconnue = derniere ?: it.reconnue,
                saisie = derniere?.adresse?.enSaisie ?: it.saisie,
                enCours = false, confirmation = null, erreurReprise = null,
            )
        }
    }

    /** L'écran de retour paraît : la biométrie, demandée d'office une seule fois. */
    override fun ouvrirDOffice() {
        if (repriseTentee) return
        repriseTentee = true
        ouvrirSessionGardee()
    }

    /** Rouvre la session gardée de la résidence reconnue : la biométrie, puis le fil. */
    override fun ouvrirSessionGardee() {
        val installation = _etat.value.reconnue ?: return
        val deverrouillage = deverrouillage ?: return
        if (_etat.value.repriseEnCours) return
        _etat.update { it.copy(repriseEnCours = true, erreurReprise = null) }
        viewModelScope.launch {
            try {
                connexion.reprendre(installation, deverrouillage)
                connues.retenir(installation)
                _etat.update { it.copy(repriseEnCours = false, ouverte = installation, ecran = EcranEntree.Espace) }
            } catch (_: RepriseAnnulee) {
                // Une annulation ne se commente pas : la session reste gardée.
                _etat.update { it.copy(repriseEnCours = false) }
            } catch (_: ReprisePerdue) {
                // Le serveur a refusé le jeton, ou la biométrie a changé : le mot de passe.
                versLeMotDePasse(installation)
                _etat.update { it.copy(repriseEnCours = false, erreurConnexion = ErreurService.SessionExpiree) }
            } catch (erreur: ErreurService) {
                _etat.update { it.copy(repriseEnCours = false, erreurReprise = erreur) }
            }
        }
    }

    /** « Utiliser le mot de passe » : la connexion, adresse et courriel pré-remplis. */
    override fun utiliserMotDePasse() {
        _etat.value.reconnue?.let(::versLeMotDePasse)
    }

    private fun versLeMotDePasse(installation: Installation) {
        _etat.update {
            it.copy(
                ecran = EcranEntree.Connexion, parMotDePasse = true, reconnue = installation,
                saisie = installation.adresse?.enSaisie ?: it.saisie, erreurReprise = null,
                email = connues.identifiant(installation) ?: it.email,
            )
        }
    }

    /** « Activer » : la session ouverte se garde, et la feuille dit ce qui la protège. */
    override fun activerGarde() {
        val ouverte = _etat.value.ouverte ?: return
        if (_etat.value.activationEnCours) return
        _etat.update { it.copy(activationEnCours = true) }
        viewModelScope.launch {
            val protection = connexion.garder(ouverte)
            _etat.update {
                it.copy(activationEnCours = false, issueGarde = protection?.let(IssueGarde::Gardee) ?: IssueGarde.Echec)
            }
        }
    }

    /** « Plus tard », « Continuer », ou la feuille balayée. */
    override fun fermerGarde() = _etat.update { it.copy(proposerGarde = false, issueGarde = null) }

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
                connues.retenirIdentifiant(etat.email, installation)
                val proposer = proposerLaGarde(installation)
                _etat.update {
                    it.copy(
                        connexionEnCours = false, motDePasse = "", ouverte = installation, ecran = EcranEntree.Espace,
                        proposerGarde = proposer,
                    )
                }
            } catch (erreur: ErreurService) {
                _etat.update { it.copy(connexionEnCours = false, erreurConnexion = erreur) }
            }
        }
    }

    /**
     * Après une connexion par le mot de passe : la session déjà gardée ici (jeton périmé,
     * biométrie changée) est remplacée sans redemander ; sinon « Garder la session ? » est
     * proposé une fois — jamais sur un appareil qui ne sait rien garder.
     */
    private suspend fun proposerLaGarde(installation: Installation): Boolean {
        if (connexion.aUneSessionGardee(installation)) {
            connexion.garder(installation)
            return false
        }
        if (!sessions.gardees.disponible || connues.gardeProposee(installation)) return false
        connues.noterGardeProposee(installation)
        return true
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
        connues.oublierGardeProposee(ouverte)
        _etat.update {
            it.copy(
                ouverte = null, ecran = EcranEntree.Connexion, parMotDePasse = true, proposerGarde = false,
                issueGarde = null, reconnue = ouverte, saisie = ouverte.adresse?.enSaisie ?: it.saisie,
                email = connues.identifiant(ouverte) ?: it.email,
            )
        }
        effacerLesPieces()
        viewModelScope.launch { connexion.deconnecter(ouverte) }
    }

    /** Comme `Parcours.oublierTout` de l'iOS : chaque session fermée, rien de retenu, l'accueil. */
    override fun oublierTout() {
        val ouverte = _etat.value.ouverte
        reconnaissance?.cancel()
        _etat.value = EtatEntree()
        connues.vider()
        sessions.gardees.vider()
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
                // Le courriel de la dernière connexion réussie ici : il ne reste que le mot de passe.
                val retenu = connues.identifiant(installation)
                _etat.update { it.copy(reconnue = installation, enCours = false, email = it.email.ifBlank { retenu.orEmpty() }) }
                // Une session gardée ici : la biométrie, sans mot de passe — sauf s'il l'a choisi.
                if (!_etat.value.parMotDePasse && connexion.aUneSessionGardee(installation)) ouvrirSessionGardee()
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
                // Garder une session demande Android 11 (le code de l'appareil y déverrouille
                // une clé) : avant, la connexion seule (divergence déclarée, `SessionsGardeesKeystore`).
                val gardees = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    SessionsGardeesKeystore(application)
                } else {
                    SessionsGardees.Indisponibles
                }
                // UNE session HTTP par hôte pour toute l'app : la connexion y pose ses
                // cookies, les écrans des menus les y relisent.
                val sessions = SessionsParHote(gardees = gardees)
                ParcoursEntree(
                    Decouverte(), sessions, Connexion(sessions), InstallationsConnues(stockage, CoffreKeystore(application)),
                    effacerLesPieces = { PiecesLocales.effacer(application) },
                )
            }
        }
    }
}
