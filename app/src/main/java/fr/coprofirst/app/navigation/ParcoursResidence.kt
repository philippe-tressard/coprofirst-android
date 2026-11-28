// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.coprofirst.api.modeles.UserRead
import fr.coprofirst.api.routes.AuthApi
import fr.coprofirst.api.routes.NotificationsApi
import fr.coprofirst.app.session.ErreurService
import fr.coprofirst.app.session.RolesConnus
import fr.coprofirst.app.session.SessionOuverte
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

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

/** GET /auth/me, `null` sur un échec : rien ne s'en bloque, le serveur tranche de toute façon. */
suspend fun ServiceCompte.moiOuRien(): UserRead? = try {
    moi()
} catch (_: ErreurService) {
    null
}

/**
 * Une ouverture de la résidence : chacune en ouvre une neuve (`id`, #407) — le parcours d'une
 * ouverture précédente du même hôte, gardé par l'activité, ne resservirait ni ses droits ni sa
 * porte. `compte` : lu AVANT d'ouvrir, à une connexion par le mot de passe (#391) ; `null`
 * sinon, et la résidence le lit.
 */
data class OuvertureResidence(val compte: UserRead?, val id: String = UUID.randomUUID().toString())

/** Ce que montre la barre d'une résidence ouverte. */
data class EtatResidence(
    /** L'identifiant de la page ouverte, ou [PLUS]. */
    val onglet: String,
    /** Les droits du compte ; avant la lecture du profil, aucun outil réservé. */
    val droits: DroitsOnglet = DroitsOnglet(),
    /** Le refus de la mesure d'audience (`opt_out_telemetrie`) ; `null` tant qu'il n'est pas lu. */
    val refusMesure: Boolean? = null,
    /** Le statut et les rôles du compte ici (GET /auth/me), pour « Vous êtes ici » (#180) ; `null` tant qu'ils ne sont pas lus. */
    val roles: RolesConnus? = null,
    /** La saisie d'une affaire du compte (`saisie_affaire_effective`, #178) ; `null` tant qu'elle n'est pas lue. */
    val saisieAffaire: String? = null,
    /** Le logement du compte (`libelle_logement`, #181), en tête du fil ; `null` sans lot servi. */
    val logement: String? = null,
    /**
     * Le mot de passe est temporaire (`mot_de_passe_a_changer` de GET /auth/me, #371) : la
     * résidence n'ouvre que « Choisir votre mot de passe » et la déconnexion. Faux tant que le
     * compte n'est pas lu — le serveur refuse de toute façon le reste (403).
     */
    val motDePasseAChanger: Boolean = false,
) {
    companion object {
        const val PLUS = "plus"
    }
}

/**
 * La résidence ouverte : la barre (ses pages, filtrées par les droits du compte), la page
 * choisie — comme `OngletsView` et `Parcours.pagesDeLaBarre` de l'iOS. Le profil lu à la
 * connexion (`lu`, #391) fait l'état dès la première image — la porte d'un mot de passe
 * temporaire, la barre du compte ; sans lui, il se relit à l'ouverture, et un échec laisse la
 * barre sans les outils réservés, sans rien bloquer. Le nombre de non lues de la cloche se
 * relit aussi (`relireNonLues`, dans `compteur`).
 */
class ParcoursResidence(
    val session: SessionOuverte,
    private val compte: ServiceCompte,
    private val compteur: CompteurNonLues = CompteurNonLues.partage,
    lu: UserRead? = null,
) : ViewModel() {
    private val pages = session.installation.pages
    private val _etat = MutableStateFlow(EtatResidence(onglet = pages.firstOrNull()?.id ?: EtatResidence.PLUS))
    val etat: StateFlow<EtatResidence> = _etat.asStateFlow()

    init {
        if (lu != null) appliquer(lu) else viewModelScope.launch { relireDroits() }
    }

    /**
     * Relit les droits du compte (GET /auth/me) — à l'ouverture, et depuis un écran après un
     * geste qui les change (un rôle qu'on se retire : `ContexteDeMenu.relireDroits`, #87),
     * comme `rafraichirProfil` de l'iOS. La barre et les onglets suivent aussitôt. Un échec
     * garde les droits d'avant ; le serveur tranche de toute façon. Rend `false` sur un échec :
     * la porte du mot de passe temporaire le dit (#371), les autres appelants l'ignorent.
     */
    suspend fun relireDroits(): Boolean {
        // Sans profil : les droits d'avant restent (ceux de tous, à l'ouverture).
        val moi = compte.moiOuRien() ?: return false
        appliquer(moi)
        return true
    }

    private fun appliquer(moi: UserRead) {
        val roles = RolesConnus(moi.statut.value, (moi.roles.orEmpty() + moi.role.value).distinct())
        _etat.update {
            it.copy(
                droits = DroitsOnglet.depuis(moi), refusMesure = moi.optOutTelemetrie, roles = roles,
                saisieAffaire = moi.saisieAffaireEffective, logement = moi.libelleLogement?.takeIf { l -> l.isNotBlank() },
                motDePasseAChanger = moi.motDePasseAChanger == true,
            )
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
