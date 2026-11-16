// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.notifications.push

import android.content.Context
import fr.coprofirst.app.session.AbonneSession
import fr.coprofirst.app.session.ErreurService
import fr.coprofirst.app.session.SessionOuverte
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Duration
import java.time.Instant

/** Ce que l'offre lit : l'autorisation, et le dernier « Plus tard ». */
data class EtatPush(val autorisation: AutorisationPush = AutorisationPush.INCONNUE, val reporteeLe: Instant? = null)

/**
 * Les notifications push de l'app (#23), comme l'iOS (`NotificationsPush`, #17 de l'iOS) :
 *
 * - l'autorisation se DEMANDE au bon moment — jamais au lancement, toujours après un geste
 *   dans la cloche (`OffrePush`) ;
 * - l'appareil s'ENREGISTRE à chaque ouverture d'une résidence (POST /appareils, qui
 *   rafraîchit `vu_le`) ;
 * - il se RETIRE à la déconnexion (DELETE /appareils/{abonnement}), avant elle ;
 * - une notification touchée mène à la cloche de SA résidence, retrouvée par son seul
 *   abonnement (`residenceDe`) : le détail se lit par l'API, jamais dans la charge.
 *
 * Tant que `relaisActif` est faux (`RelaisPush`), rien de tout cela ne se fait : ni offre,
 * ni jeton, ni requête. Rien de ce qui passe ici ne s'écrit dans un journal.
 */
class NotificationsPush(
    private val centre: CentreNotifications,
    private val service: ServiceAppareils,
    private val reglages: ReglagesPush,
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

    /** L'offre s'affiche : relais actif, autorisation jamais demandée, pas de « Plus tard » récent. */
    fun offreDue(etat: EtatPush = _etat.value): Boolean {
        if (!relaisActif || etat.autorisation != AutorisationPush.NON_DEMANDEE) return false
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
        lireAutorisation()
        if (_etat.value.autorisation != AutorisationPush.ACCORDEE) return
        enregistrer(session)
        // Le système redonne le jeton à chaque inscription (et le nouveau s'il a changé).
        centre.inscrire()
    }

    private suspend fun enregistrer(session: SessionOuverte) {
        val residence = session.installation.url
        val jeton = jeton ?: return
        if (!relaisActif || residence in enregistrees) return
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

    companion object {
        /** Après « Plus tard », l'offre se tait ce temps (l'iOS : 14 jours). */
        val REPORT: Duration = Duration.ofDays(14)

        /** La seule clé de la charge que lit l'app : l'abonnement (spec push §5). */
        const val CLE_ABONNEMENT = "a"
        private const val CLE_REPORT = "offre-reportee"

        private fun lireInstant(texte: String): Instant? = runCatching { Instant.parse(texte) }.getOrNull()

        @Volatile
        private var instance: NotificationsPush? = null

        /** Une seule par processus : l'offre de la cloche et le branchement à venir la partagent. */
        fun de(contexte: Context): NotificationsPush = instance ?: synchronized(this) {
            instance ?: run {
                val app = contexte.applicationContext
                val reglages = ReglagesPreferences(app)
                // Aucun fournisseur de jeton tant que le SDK n'est pas décidé (`RelaisPush`).
                NotificationsPush(CentreAndroid(app, reglages, null), ServiceAppareilsApi, reglages, EnvironnementPush.de(app))
            }.also { instance = it }
        }
    }
}
