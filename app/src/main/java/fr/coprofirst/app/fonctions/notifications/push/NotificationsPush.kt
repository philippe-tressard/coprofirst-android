// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.notifications.push

import android.content.Context
import fr.coprofirst.app.commun.ReglagesAppareil
import fr.coprofirst.app.commun.ReglagesPreferences
import fr.coprofirst.app.session.AbonneSession
import fr.coprofirst.app.session.ErreurService
import fr.coprofirst.app.session.Installation
import fr.coprofirst.app.session.SessionOuverte
import fr.coprofirst.app.session.VersionContrat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Duration
import java.time.Instant

/** Ce que l'app fait d'une notification arrivée app ouverte. */
sealed interface ArriveePush {
    /** L'abonnement n'est plus de cet appareil : rien. */
    data object Ignoree : ArriveePush

    /** La résidence à l'écran : sa cloche se relit, l'avis se range sans bruit. */
    data class ResidenceOuverte(val residence: String) : ArriveePush

    /** Une autre résidence de l'appareil : l'avertissement sonne. */
    data object Avertir : ArriveePush
}

/** Ce que l'offre lit : l'autorisation, et le dernier « Plus tard ». */
data class EtatPush(val autorisation: AutorisationPush = AutorisationPush.INCONNUE, val reporteeLe: Instant? = null)

/**
 * Les notifications push de l'app (#280), comme l'iOS (`NotificationsPush`, #17 de l'iOS) :
 *
 * - l'autorisation se DEMANDE au bon moment — jamais au lancement, toujours après un geste
 *   dans la cloche (`OffrePush`) ;
 * - l'appareil s'ENREGISTRE à chaque ouverture d'une résidence (POST /appareils, qui
 *   rafraîchit `vu_le`) ;
 * - il se RETIRE à la déconnexion (DELETE /appareils/{abonnement}), avant elle ;
 * - une notification touchée mène à la cloche de SA résidence, retrouvée par son seul
 *   abonnement (`residenceDe`) : le détail se lit par l'API, jamais dans la charge ;
 * - arrivée app ouverte, sur la résidence à l'écran : la cloche se relit, sans bruit
 *   (`arrivee`) ; pour une autre, l'avertissement sonne (`AvertissementPush`).
 *
 * Seule une résidence qui dit servir le plancher reçoit le jeton (`Installation.sertLePlancher`) :
 * le plancher relaie FCM (depuis le contrat 2.10.0, #311) ; un contrat absent ou illisible, rien. Tant
 * que `relaisActif` est faux (`RelaisPush`, ou pas de configuration Firebase), rien de tout
 * cela ne se fait : ni offre, ni jeton, ni requête. Rien de ce qui passe ici ne s'écrit dans un journal.
 */
class NotificationsPush(
    private val centre: CentreNotifications,
    private val service: ServiceAppareils,
    private val reglages: ReglagesAppareil,
    private val environnement: EnvironnementPush,
    val relaisActif: Boolean = RelaisPush.ACTIF,
    private val maintenant: () -> Instant = Instant::now,
) : AbonneSession {
    val abonnements = AbonnementsPush(reglages)
    private val _etat = MutableStateFlow(EtatPush(reporteeLe = reglages.lire(CLE_REPORT)?.let(::lireInstant)))
    val etat: StateFlow<EtatPush> = _etat.asStateFlow()

    private var jeton: JetonPush? = null
    private var ouverte: SessionOuverte? = null

    /** Les résidences où le jeton courant est enregistré depuis le lancement : une fois chacune. */
    private val enregistrees = mutableSetOf<String>()

    fun lireAutorisation() = _etat.update { it.copy(autorisation = centre.autorisation()) }

    // L'autorisation, au bon moment

    /**
     * L'offre s'affiche dans la cloche de cette résidence : relais actif, plancher servi,
     * autorisation jamais demandée, pas de « Plus tard » récent.
     */
    fun offreDue(installation: Installation, etat: EtatPush = _etat.value): Boolean {
        if (!relaisActif || !installation.sertLePlancher) return false
        if (etat.autorisation != AutorisationPush.NON_DEMANDEE) return false
        val reportee = etat.reporteeLe ?: return true
        return Duration.between(reportee, maintenant()) > REPORT
    }

    /** Le système a répondu à « Activer » : si c'est oui, le jeton est demandé. */
    fun apresDemande(accordee: Boolean) {
        centre.demandeFaite()
        lireAutorisation()
        if (accordee && relaisActif) centre.inscrire()
    }

    /** « Plus tard » : l'offre se tait `REPORT`. */
    fun reporter() {
        val le = maintenant()
        reglages.ecrire(CLE_REPORT, le.toString())
        _etat.update { it.copy(reporteeLe = le) }
    }

    // L'appareil auprès de chaque résidence

    /**
     * Le jeton est arrivé (le fournisseur l'appelle, dans sa coroutine) : il s'enregistre
     * auprès de la résidence ouverte.
     */
    suspend fun jetonRecu(neuf: JetonPush) {
        if (neuf != jeton) {
            jeton = neuf
            enregistrees.clear()
        }
        ouverte?.let { enregistrer(it) }
    }

    /**
     * Une résidence s'ouvre, sa session en main : l'appareil s'y enregistre si la personne
     * a autorisé les notifications ; sinon rien ne part.
     */
    override suspend fun residenceOuverte(session: SessionOuverte) {
        if (!relaisActif) return
        ouverte = session
        if (!session.installation.sertLePlancher) return
        lireAutorisation()
        if (_etat.value.autorisation != AutorisationPush.ACCORDEE) return
        enregistrer(session)
        // Le système redonne le jeton à chaque inscription (et le nouveau s'il a changé).
        centre.inscrire()
    }

    private suspend fun enregistrer(session: SessionOuverte) {
        val residence = session.installation.url
        val jeton = jeton ?: return
        if (!relaisActif || !session.installation.sertLePlancher || residence in enregistrees) return
        enregistrees += residence
        try {
            service.enregistrer(AppareilPush(jeton, environnement, abonnements.abonnement(residence)), session)
        } catch (_: ErreurService) {
            // La cloche reste le chemin de l'information (spec push N9) : on réessaiera à la
            // prochaine ouverture. Rien ne se journalise.
            enregistrees -= residence
        }
    }

    /**
     * Avant la déconnexion : l'appareil se retire, l'abonnement s'oublie — même si le
     * serveur ne répond pas (l'installation le purgera, et une notification qui arriverait
     * encore ne mène plus à rien).
     */
    override suspend fun residenceFermee(session: SessionOuverte) {
        val residence = session.installation.url
        enregistrees -= residence
        // Par la résidence, pas par l'objet : le parcours donne une session neuve à chaque geste.
        if (ouverte?.installation?.url == residence) ouverte = null
        val abonnement = abonnements.existant(residence) ?: return
        abonnements.oublier(residence)
        try {
            service.retirer(abonnement, session)
        } catch (_: ErreurService) {
            // L'installation le purgera (60 jours sans enregistrement).
        }
    }

    // La notification touchée

    /** La résidence (son adresse) d'une notification touchée — `null` si l'abonnement n'est plus de cet appareil. */
    fun residenceDe(abonnement: String?): String? = abonnement?.let(abonnements::residence)

    /**
     * Une notification arrivée app ouverte (`ServiceFcm`) : ce que l'app en fait, comme
     * `presentation` de l'iOS — la résidence à l'écran se relit sans bruit, une autre sonne,
     * un abonnement inconnu (résidence quittée) ne montre rien.
     */
    fun arrivee(abonnement: String?): ArriveePush {
        val residence = residenceDe(abonnement) ?: return ArriveePush.Ignoree
        return if (ouverte?.installation?.url == residence) ArriveePush.ResidenceOuverte(residence) else ArriveePush.Avertir
    }

    companion object {
        /** Après « Plus tard », l'offre se tait ce temps (l'iOS : 14 jours). */
        val REPORT: Duration = Duration.ofDays(14)

        /** La seule clé de la charge que lit l'app : l'abonnement (spec push §5). */
        const val CLE_ABONNEMENT = "a"
        private const val CLE_REPORT = "offre-reportee"

        private fun lireInstant(texte: String): Instant? = runCatching { Instant.parse(texte) }.getOrNull()

        @Volatile
        private var instance: NotificationsPush? = null

        /** Le fichier des réglages du push, à part de celui de la montre (`montre`). */
        private const val FICHIER = "push"

        /**
         * Où le jeton reçu de Firebase rejoint l'app : le fil principal, comme les abonnés de
         * session. Paresseuse : le fil principal n'existe pas hors d'Android (tests JVM).
         */
        val portee by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }

        /**
         * Une seule par processus : l'offre de la cloche, les abonnés de session et le service
         * FCM la partagent. Sans configuration Firebase injectée à la construction
         * (`ConfigFirebase`), aucun fournisseur : le relais est tenu pour éteint.
         */
        fun de(contexte: Context): NotificationsPush = instance ?: synchronized(this) {
            instance ?: run {
                val app = contexte.applicationContext
                val reglages = ReglagesPreferences(app, FICHIER)
                val fournisseur = if (RelaisPush.ACTIF) ConfigFirebase.fournisseur(app) else null
                NotificationsPush(
                    CentreAndroid(app, reglages, fournisseur), ServiceAppareilsApi, reglages, EnvironnementPush.de(app),
                    relaisActif = fournisseur != null,
                )
            }.also { instance = it }
        }
    }
}
