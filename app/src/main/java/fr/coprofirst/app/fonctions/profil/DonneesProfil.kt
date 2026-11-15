// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.profil

import fr.coprofirst.api.modeles.DemandeSuppressionRead
import fr.coprofirst.api.modeles.EtatDemandeSuppression
import fr.coprofirst.api.modeles.MeUpdate
import fr.coprofirst.api.modeles.StatutDemandeSuppression
import fr.coprofirst.api.modeles.UserRead
import java.time.Instant

/** Une saisie vide part à `null` — le site fait de même. */
fun String.nulSiVide(): String? = trim().ifEmpty { null }

/**
 * Ce qu'on corrige de son identité (PATCH /auth/me), comme l'iOS (`SaisieIdentite`).
 * Changer d'adresse demande le mot de passe actuel ; la nouvelle ne vaut qu'une fois confirmée.
 */
data class SaisieIdentite(
    val prenom: String = "",
    val nom: String = "",
    val email: String = "",
    val telephone: String = "",
    val societe: String = "",
    val fonction: String = "",
    val motDePasseActuel: String = "",
) {
    /** L'adresse change-t-elle ? Comparée sans casse ni espaces autour, comme le site. */
    fun changeAdresse(ancienne: String): Boolean = email.trim().lowercase() != ancienne.trim().lowercase()

    /** Prénom, nom et adresse sont obligatoires ; une nouvelle adresse demande le mot de passe. */
    fun complete(ancienne: String): Boolean =
        listOf(prenom, nom, email).all { it.isNotBlank() } && (!changeAdresse(ancienne) || motDePasseActuel.isNotEmpty())

    /** Le corps envoyé : l'adresse et le mot de passe seulement si l'adresse change. */
    fun versMeUpdate(ancienne: String): MeUpdate {
        val nouvelle = changeAdresse(ancienne)
        return MeUpdate(
            prenom = prenom.trim(),
            nom = nom.trim(),
            email = if (nouvelle) email.trim() else null,
            motDePasseActuel = if (nouvelle) motDePasseActuel else null,
            telephone = telephone.nulSiVide(),
            societe = societe.nulSiVide(),
            fonction = fonction.nulSiVide(),
        )
    }

    companion object {
        fun depuis(moi: UserRead) = SaisieIdentite(
            prenom = moi.prenom, nom = moi.nom, email = moi.email,
            telephone = moi.telephone.orEmpty(), societe = moi.societe.orEmpty(), fonction = moi.fonction.orEmpty(),
        )
    }
}

/** Ce qui manque encore pour changer de mot de passe, dans l'ordre du site ; `null` : prêt. */
enum class AttenteMotDePasse { Actuel, Nouveau, Confirmation }

/**
 * ⚠️ Divergence déclarée : l'iOS dit aussi la longueur minimale avant l'envoi ; elle n'est
 * pas publiée dans `tables-clients.json` (coprofirst#2755). Un mot de passe trop court est
 * refusé par la phrase du serveur.
 */
fun attenteMotDePasse(actuel: String, nouveau: String, confirmation: String): AttenteMotDePasse? = when {
    actuel.isEmpty() -> AttenteMotDePasse.Actuel
    nouveau.isEmpty() -> AttenteMotDePasse.Nouveau
    nouveau != confirmation -> AttenteMotDePasse.Confirmation
    else -> null
}

/** Ma demande de suppression ouverte (`DemandeSuppressionRead`, coprofirst#2699). */
data class DemandeSuppressionCompte(val creeLe: Instant, val delaiJours: Int?)

/**
 * GET /auth/demande-suppression, comme l'iOS (`EtatSuppressionCompte`) : ma demande
 * OUVERTE (une demande traitée ou retirée n'en est plus une), le délai annoncé et ce qui
 * sera effacé — SERVIS : l'app ne les écrit nulle part.
 */
data class EtatSuppressionCompte(
    val demande: DemandeSuppressionCompte?,
    val delaiJours: Int?,
    val consequences: List<String>,
) {
    companion object {
        fun depuis(lu: EtatDemandeSuppression) = EtatSuppressionCompte(
            demande = lu.demande?.takeIf { it.statut == StatutDemandeSuppression.OUVERTE }?.let(::demande),
            delaiJours = lu.delaiAnnonceJours,
            consequences = lu.consequences,
        )

        private fun demande(lu: DemandeSuppressionRead) = DemandeSuppressionCompte(lu.creeLe.toInstant(), lu.delaiAnnonceJours)
    }
}
