// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.profil

import fr.coprofirst.api.modeles.BatimentPublic
import fr.coprofirst.api.modeles.ChangePasswordBody
import fr.coprofirst.api.modeles.DemandeModifCreate
import fr.coprofirst.api.modeles.DemandeModifRead
import fr.coprofirst.api.modeles.DemandeSuppressionCreate
import fr.coprofirst.api.modeles.MeUpdate
import fr.coprofirst.api.modeles.OptOutTelemetrieBody
import fr.coprofirst.api.modeles.UserRead
import fr.coprofirst.api.routes.AuthApi
import fr.coprofirst.app.session.SessionOuverte
import kotlinx.serialization.json.Json

/**
 * Ce que l'écran Profil demande à la résidence ouverte, comme l'iOS (`ServiceProfil`,
 * `ServiceSuppressionCompte`). Les mots de passe ne partent qu'à l'hôte de la session
 * ouverte — confirmé par la garde de l'hôte à la connexion.
 */
interface ServiceProfil {
    /** GET /auth/me. */
    suspend fun moi(): UserRead

    /** PATCH /auth/me : l'identité ; `true` si un lien de confirmation est parti à une nouvelle adresse. */
    suspend fun modifierIdentite(saisie: SaisieIdentite, ancienne: String): Boolean

    /** POST /auth/change-password : un refus porte la phrase du serveur. */
    suspend fun changerMotDePasse(actuel: String, nouveau: String)

    /** PATCH /auth/me : la saisie d'une affaire du compte (`saisie_affaire`), pour tous ses appareils. */
    suspend fun choisirSaisie(mode: String)

    /** PATCH /auth/me/opt-out-telemetrie. */
    suspend fun refuserTelemetrie(refus: Boolean)

    /** GET /auth/me/telemetrie : le JSON servi, mis en forme pour être partagé. */
    suspend fun exporterTelemetrie(): String

    /** DELETE /auth/me/telemetrie. */
    suspend fun effacerTelemetrie()

    /** GET /auth/me/demandes-modification. */
    suspend fun demandesModification(): List<DemandeModifRead>

    /** POST /auth/me/demande-modification : le conseil syndical valide. */
    suspend fun demanderModification(statut: String?, batiment: Int?, motif: String)

    /** GET /auth/batiments. */
    suspend fun batiments(): List<BatimentPublic>

    /** GET /auth/demande-suppression : ma demande ouverte, le délai et ce qui sera effacé, SERVIS. */
    suspend fun etatSuppression(): EtatSuppressionCompte

    /** POST /auth/demande-suppression : « Mot de passe incorrect. », « déjà en cours » sont des phrases du serveur. */
    suspend fun demanderSuppression(motDePasse: String, motif: String)

    /** DELETE /auth/demande-suppression : retirée tant qu'elle n'est pas traitée. */
    suspend fun retirerDemandeSuppression()
}

/** Le Profil par le client GÉNÉRÉ, à travers la porte de la session ouverte. */
class ServiceProfilApi(private val session: SessionOuverte) : ServiceProfil {
    private suspend fun <R> auth(requete: AuthApi.() -> R): R = session.appeler { base, client -> AuthApi(base, client).requete() }

    override suspend fun moi(): UserRead = auth { getAuthMe() }

    override suspend fun choisirSaisie(mode: String) {
        auth { patchAuthMe(MeUpdate(saisieAffaire = mode)) }
    }

    override suspend fun modifierIdentite(saisie: SaisieIdentite, ancienne: String): Boolean {
        auth { patchAuthMe(saisie.versMeUpdate(ancienne)) }
        return saisie.changeAdresse(ancienne)
    }

    override suspend fun changerMotDePasse(actuel: String, nouveau: String) =
        auth { postAuthChangePassword(ChangePasswordBody(motDePasseActuel = actuel, nouveauMotDePasse = nouveau)) }

    override suspend fun refuserTelemetrie(refus: Boolean) = auth { patchAuthMeOptOutTelemetrie(OptOutTelemetrieBody(refus)) }

    override suspend fun exporterTelemetrie(): String = JSON_LISIBLE.encodeToString(auth { getAuthMeTelemetrie() })

    override suspend fun effacerTelemetrie() = auth { deleteAuthMeTelemetrie() }

    override suspend fun demandesModification(): List<DemandeModifRead> = auth { getAuthMeDemandesModification() }

    override suspend fun demanderModification(statut: String?, batiment: Int?, motif: String) {
        auth { postAuthMeDemandeModification(DemandeModifCreate(statutSouhaite = statut, batimentIdSouhaite = batiment, motif = motif.nulSiVide())) }
    }

    override suspend fun batiments(): List<BatimentPublic> = auth { getAuthBatiments() }

    override suspend fun etatSuppression(): EtatSuppressionCompte = EtatSuppressionCompte.depuis(auth { getAuthDemandeSuppression() })

    override suspend fun demanderSuppression(motDePasse: String, motif: String) {
        auth { postAuthDemandeSuppression(DemandeSuppressionCreate(motDePasse = motDePasse, motif = motif.nulSiVide())) }
    }

    override suspend fun retirerDemandeSuppression() = auth { deleteAuthDemandeSuppression() }

    private companion object {
        val JSON_LISIBLE = Json { prettyPrint = true }
    }
}
