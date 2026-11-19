// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.entree

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.coprofirst.app.commun.GestesDeSortie
import fr.coprofirst.app.commun.ResidenceConnue
import fr.coprofirst.app.commun.pieces.PiecesLocales
import fr.coprofirst.app.session.AbonnesSession
import fr.coprofirst.app.session.AdresseInstallation
import fr.coprofirst.app.session.Connexion
import fr.coprofirst.app.session.Decouverte
import fr.coprofirst.app.session.Deverrouillage
import fr.coprofirst.app.session.ErreurService
import fr.coprofirst.app.session.GardeHote
import fr.coprofirst.app.session.Installation
import fr.coprofirst.app.session.InstallationsConnues
import fr.coprofirst.app.session.LectureDisponible
import fr.coprofirst.app.session.OrigineAdresse
import fr.coprofirst.app.session.PreuveHote
import fr.coprofirst.app.session.RepriseAnnulee
import fr.coprofirst.app.session.ReprisePerdue
import fr.coprofirst.app.session.Reseau
import fr.coprofirst.app.session.SessionOuverte
import fr.coprofirst.app.session.SessionsParHote
import fr.coprofirst.app.session.cleCoffre
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
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
    /** Avant de fermer une session : ce qui doit encore partir chez elle (la mesure d'audience, #144). */
    private val avantSortie: suspend () -> Unit = {},
    /**
     * La session de cette résidence est close — déconnectée, ou refusée pour de bon par le
     * serveur : ce qui se noterait encore pour elle se jette (la mesure d'audience, #162).
     */
    private val apresSortie: (Installation) -> Unit = {},
    /** L'appareil est-il en ligne ? (#28) : le bandeau, et la reprise au retour du réseau. */
    val reseau: Reseau = Reseau.Fige(),
    /** Ce qui suit l'ouverture et la fermeture d'une résidence : le push, la montre (#150). */
    private val abonnes: AbonnesSession = AbonnesSession.Aucun,
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
    private var repriseEnLigneEnCours = false

    init {
        viewModelScope.launch { sessions.refusees.collect(::sessionRefusee) }
        // Chaque résidence ouverte, par quelque chemin que ce soit, prévient ses abonnés (#150).
        viewModelScope.launch {
            etat.map { it.ouverte }.distinctUntilChangedBy { it?.url }.filterNotNull().collect { abonnes.ouverte(session(it)) }
        }
        // Au lancement, comme l'iOS (`purgerLesLectures`) : rien ne reste d'une lecture
        // périmée, ni d'une résidence dont la session n'est plus gardée.
        sessions.lectures?.purger(
            connues.liste.filter(connexion::aUneSessionGardee).mapNotNull { it.adresse?.cleCoffre }.toSet(),
        )
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
        _etat.update { it.vers(EcranEntree.Connexion).copy(aideDemo = null) }
        if (_etat.value.saisie.isEmpty() && derniere != null) saisir(derniere, pause = false)
    }

    /** La démonstration : ses trois rôles (`DemoView` de l'iOS). */
    override fun entrerDemo() = _etat.update { it.vers(EcranEntree.Demo) }

    override fun enSavoirPlus() = _etat.update { it.vers(EcranEntree.EnSavoirPlus) }

    /**
     * Un rôle de la démo, comme l'iOS sans route qui serve les identifiants : la connexion,
     * à l'adresse de la démo, l'identifiant public du rôle (`user-cs@demo.coprofirst.fr`)
     * pré-rempli, et où lire son mot de passe. La démo ne se confirme pas (`GardeHote`).
     */
    override fun choisirPersonnage(code: String, role: String) {
        val demo = AdresseInstallation.DEMO.enSaisie
        _etat.update {
            it.vers(EcranEntree.Connexion).copy(email = "$code@$demo", motDePasse = "", aideDemo = role, erreurConnexion = null)
        }
        saisir(demo, pause = false)
    }

    override fun saisir(texte: String) = saisir(texte, pause = true)

    override fun lireQR(texte: String) {
        AdresseInstallation.normaliser(texte)?.let { recevoir(it, OrigineAdresse.Scan) }
    }

    /** Un lien d'application (#7) : `Sollicitation.deLien`. */
    fun suivreLien(lien: String) = Sollicitation.deLien(lien)?.let(::suivre)

    /** Une notification push touchée (#280), `residence` retrouvée par son abonnement : `Sollicitation.deNotification`. */
    fun suivreNotification(residence: String) = Sollicitation.deNotification(residence, connues.liste)?.let(::suivre)

    /**
     * Vers la résidence ouverte : elle reste, et l'écran visé se pose tout de suite. Vers une
     * autre : la connexion, à son adresse — la session ouverte est quittée sans être fermée
     * (`changer` de l'iOS) ; une résidence connue s'y reprend comme d'habitude, un hôte jamais
     * vu se découvre (`recevoir`). L'écran visé attend que sa résidence soit ouverte, puis la
     * porte le pose (#147). Rien ne part de plus.
     */
    private fun suivre(sollicitation: Sollicitation) {
        val (adresse, enAttente) = sollicitation
        val etat = _etat.value
        if (etat.ouverte?.adresse == adresse ||
            (etat.ecran == EcranEntree.Connexion && etat.reconnue?.adresse == adresse)
        ) {
            _etat.update { it.copy(lien = enAttente) }
            return
        }
        reconnaissance?.cancel()
        _etat.update {
            it.copy(
                ecran = EcranEntree.Connexion, pile = emptyList(), aideDemo = null,
                ouverte = null, confirmation = null, venue = null,
                motDePasse = "", connexionEnCours = false, erreurConnexion = null, lien = enAttente,
            )
        }
        if (connues.liste.any { it.adresse == adresse }) saisir(adresse.enSaisie, pause = false)
        else recevoir(adresse, OrigineAdresse.Lien)
    }

    /** La porte a posé l'écran du lien : il ne se repose pas. */
    fun lienSuivi() = _etat.update { it.copy(lien = null) }

    /**
     * Une adresse que l'utilisateur n'a pas écrite : elle remplit le champ et se reconnaît
     * sans attendre, et son origine suit jusqu'à « Se connecter » — où la garde exige
     * toujours la confirmation de l'hôte, résidence connue ou démonstration comprise.
     */
    private fun recevoir(adresse: AdresseInstallation, origine: OrigineAdresse) {
        _etat.update { it.copy(venue = AdresseVenue(adresse, origine)) }
        saisir(adresse.enSaisie, pause = false)
    }

    override fun saisirEmail(texte: String) = _etat.update { it.copy(email = texte, erreurConnexion = null) }

    override fun saisirMotDePasse(texte: String) = _etat.update { it.copy(motDePasse = texte, erreurConnexion = null) }

    override fun basculerVoirMotDePasse() = _etat.update { it.copy(voirMotDePasse = !it.voirMotDePasse) }

    /** L'écran d'avant ; quitter la connexion y arrête tout ce qui courait. */
    override fun retour() {
        if (_etat.value.ecran == EcranEntree.Connexion) {
            reconnaissance?.cancel()
            _etat.update { it.copy(enCours = false, confirmation = null, venue = null, aideDemo = null) }
        }
        _etat.update { it.copy(ecran = it.pile.lastOrNull() ?: EcranEntree.Accueil, pile = it.pile.dropLast(1)) }
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
        _etat.update { it.copy(repriseEnCours = true, erreurReprise = null, lecturePerimee = false) }
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
                apresSortie(installation)
                versLeMotDePasse(installation)
                _etat.update { it.copy(repriseEnCours = false, erreurConnexion = ErreurService.SessionExpiree) }
            } catch (erreur: ErreurService) {
                if (!entrerHorsLigne(installation, erreur)) _etat.update { it.copy(repriseEnCours = false, erreurReprise = erreur) }
            }
        }
    }

    /**
     * La reprise vient d'échouer, la biométrie passée : faute de réseau et avec une lecture
     * gardée, la résidence s'ouvre en consultation (#28). Rend `true` si l'écran a changé.
     */
    private fun entrerHorsLigne(installation: Installation, erreur: ErreurService): Boolean {
        if (erreur != ErreurService.Injoignable && erreur != ErreurService.HorsLigne) return false
        when (val lecture = connexion.consulterHorsLigne(installation)) {
            is LectureDisponible.Gardee -> _etat.update {
                it.copy(repriseEnCours = false, ouverte = installation, ecran = EcranEntree.Espace, consultation = lecture.aJourAu)
            }
            LectureDisponible.Perimee -> _etat.update { it.copy(repriseEnCours = false, lecturePerimee = true) }
            LectureDisponible.Aucune -> return false
        }
        return true
    }

    /**
     * Le réseau est revenu pendant une consultation : la session se rouvre sans redemander
     * la biométrie, et l'espace se reconstruit — tout se relit. Un refus du serveur a tout
     * effacé : retour à la connexion. Toujours sans réseau : la consultation continue.
     */
    override fun reprendreEnLigne() {
        val ouverte = _etat.value.ouverte ?: return
        if (_etat.value.consultation == null || repriseEnLigneEnCours) return
        repriseEnLigneEnCours = true
        viewModelScope.launch {
            try {
                connexion.reprendreEnLigne(ouverte)
                _etat.update { if (it.ouverte == ouverte) it.copy(consultation = null) else it }
            } catch (_: ReprisePerdue) {
                if (_etat.value.ouverte == ouverte) quitterLaSessionRefusee(ouverte)
            } catch (_: ErreurService) {
                // Toujours sans réseau : la consultation continue.
            } finally {
                repriseEnLigneEnCours = false
            }
        }
    }

    /**
     * Le serveur refuse la session ouverte en cours d'usage, renouvellement compris (#201,
     * coprofirst-ios#248) : la connexion pré-remplie, la raison dite, au lieu d'un
     * « Réessayer » sans issue sur chaque écran. Une seule fois, même si plusieurs écrans
     * sont refusés ensemble ; en consultation, la reprise en ligne en décide.
     */
    private fun sessionRefusee(adresse: AdresseInstallation) {
        val etat = _etat.value
        val ouverte = etat.ouverte ?: return
        if (ouverte.adresse == adresse && etat.consultation == null) quitterLaSessionRefusee(ouverte)
    }

    /** Quitter la résidence dont le serveur refuse la session : ce qui restait d'elle se jette. */
    private fun quitterLaSessionRefusee(installation: Installation) {
        viewModelScope.launch { abonnes.fermee(session(installation)) }
        apresSortie(installation)
        effacerLesPieces()
        versLeMotDePasse(installation)
        _etat.update {
            it.copy(
                ouverte = null, consultation = null, proposerGarde = false, issueGarde = null,
                erreurConnexion = ErreurService.SessionExpiree,
            )
        }
    }

    /** « Utiliser le mot de passe » : la connexion, adresse et courriel pré-remplis. */
    override fun utiliserMotDePasse() {
        _etat.value.reconnue?.let { versLeMotDePasse(it, empiler = true) }
    }

    /**
     * La connexion, adresse et courriel pré-remplis. `empiler` : le retour ramène à l'écran
     * de retour (la session y reste gardée) ; sinon la connexion est la racine.
     */
    private fun versLeMotDePasse(installation: Installation, empiler: Boolean = false) {
        _etat.update {
            (if (empiler) it.vers(EcranEntree.Connexion) else it.copy(ecran = EcranEntree.Connexion, pile = emptyList())).copy(
                parMotDePasse = true, reconnue = installation,
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
        // L'origine d'une adresse venue d'un QR ou d'un lien vaut tant que le champ la porte.
        val origine = etat.venue?.takeIf { it.adresse == adresse }?.origine ?: OrigineAdresse.Saisie
        if (garde.exigeConfirmation(adresse, origine)) {
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
                        proposerGarde = proposer, avisSortie = null,
                        // Le lien d'une autre résidence tombe : l'utilisateur est allé ailleurs.
                        pile = emptyList(), aideDemo = null, lien = it.lien?.takeIf { l -> l.adresse == installation.adresse },
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
    fun sessionOuverte(): SessionOuverte? = _etat.value.ouverte?.let(::session)

    private fun session(installation: Installation) = SessionOuverte(installation, sessions, baseApi = baseApi)

    override fun deconnecter() {
        val ouverte = _etat.value.ouverte ?: return
        connues.oublierGardeProposee(ouverte)
        _etat.update {
            it.copy(
                ouverte = null, ecran = EcranEntree.Connexion, parMotDePasse = true, proposerGarde = false, consultation = null,
                issueGarde = null, reconnue = ouverte, saisie = ouverte.adresse?.enSaisie ?: it.saisie,
                email = connues.identifiant(ouverte) ?: it.email, lien = null, pile = emptyList(),
            )
        }
        effacerLesPieces()
        viewModelScope.launch {
            avantSortie()
            abonnes.fermee(session(ouverte))
            apresSortie(ouverte)
            if (!connexion.deconnecter(ouverte)) _etat.update { it.copy(avisSortie = AvisSortie.SessionInjoignable) }
        }
    }

    override fun changerDeResidence(installation: Installation?) {
        // Les pages vues partent tant que la session les rattache au compte ; elle reste ouverte.
        viewModelScope.launch { avantSortie() }
        reconnaissance?.cancel()
        _etat.update {
            it.copy(
                ecran = EcranEntree.Connexion, pile = emptyList(), ouverte = null, consultation = null, confirmation = null,
                venue = null, proposerGarde = false, issueGarde = null, lien = null, aideDemo = null, reconnue = null,
                saisie = "", email = "", motDePasse = "", parMotDePasse = false, connexionEnCours = false,
                erreurConnexion = null, avisSortie = null,
            )
        }
        // Reconnue de nouveau : son courriel pré-rempli, sa session gardée rouverte par la biométrie.
        installation?.adresse?.let { saisir(it.enSaisie, pause = false) }
    }

    override fun residencesConnues(): List<ResidenceConnue> = connues.liste.map { ResidenceConnue(it, connues.roles(it)) }

    override fun noterRoles(installation: Installation, statut: String?, roles: List<String>) = connues.noterRoles(installation, statut, roles)

    /**
     * Comme `Parcours.oublierTout` de l'iOS : la session de CHAQUE résidence connue fermée sur
     * son serveur (#177), rien de retenu, l'accueil — et si certaines n'ont pas répondu, la
     * connexion le dit. Une session gardée sous la biométrie ne se lit pas sans elle : celles
     * des résidences non ouvertes partent sans leur jeton, comme sur l'iOS, et le serveur les
     * révoque à leur expiration.
     */
    override fun oublierTout() {
        val ouverte = _etat.value.ouverte
        val residences = (listOfNotNull(ouverte) + connues.liste).distinctBy { it.url }
        reconnaissance?.cancel()
        _etat.value = EtatEntree()
        connues.vider()
        sessions.gardees.vider()
        sessions.lectures?.purger()
        effacerLesPieces()
        viewModelScope.launch {
            avantSortie()
            var injoignables = 0
            for (residence in residences) {
                abonnes.fermee(session(residence))
                apresSortie(residence)
                if (!connexion.deconnecter(residence)) injoignables++
            }
            sessions.oublierTout()
            if (injoignables > 0) _etat.update { it.copy(avisSortie = AvisSortie.ResidencesInjoignables) }
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
    }
}
