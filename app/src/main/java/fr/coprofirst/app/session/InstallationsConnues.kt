// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.session

import android.content.Context
import androidx.core.content.edit
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Le statut et les rôles du compte dans une résidence (`statut`, `roles` de GET /auth/me). */
@Serializable
data class RolesConnus(val statut: String?, val roles: List<String>)

/** Où se gardent les résidences connues — une chaîne, sur l'appareil seul. */
interface StockageResidences {
    fun lire(): String?
    fun ecrire(valeur: String)
}

/** Les préférences privées de l'app — exclues de toute sauvegarde (`regles_*.xml`). */
class StockagePreferences(contexte: Context) : StockageResidences {
    private val preferences = contexte.getSharedPreferences("residences", Context.MODE_PRIVATE)
    override fun lire(): String? = preferences.getString(CLE, null)
    override fun ecrire(valeur: String) = preferences.edit { putString(CLE, valeur) }

    private companion object {
        const val CLE = "installations-connues"
    }
}

/**
 * Les résidences déjà ouvertes sur cet appareil, la plus récente en tête : leur adresse,
 * leur nom, leur contrat. La dernière pré-remplit l'écran de connexion, comme sur iOS. Pas d'annuaire — une résidence n'apparaît ici que si
 * l'utilisateur l'a donnée. Rien n'est envoyé ailleurs.
 *
 * Ce qui dit QUI est l'utilisateur — le courriel de la dernière connexion réussie — va au
 * coffre du compte, chiffré, jamais avec les résidences (#13, comme l'iOS #84).
 */
class InstallationsConnues(
    private val stockage: StockageResidences,
    private val coffre: CoffreDuCompte = CoffreEnMemoire(),
) {
    var liste: List<Installation> = lireListe()
        private set

    /** Retient une résidence ouverte : en tête, une seule fois par adresse. */
    fun retenir(installation: Installation) {
        liste = listOf(installation) + liste.filterNot { it.url == installation.url }
        enregistrer()
    }

    /** La dernière résidence ouverte : celle que la biométrie rouvre au retour. */
    val derniere: Installation? get() = liste.firstOrNull()

    /** Le courriel de la dernière connexion réussie à cette résidence. */
    fun identifiant(installation: Installation): String? = coffre.lire(PREFIXE_IDENTIFIANT + cle(installation))

    fun retenirIdentifiant(email: String, installation: Installation) {
        coffre.ranger(email.trim(), PREFIXE_IDENTIFIANT + cle(installation))
    }

    /** « Garder la session » a déjà été proposé ici : il ne l'est qu'une fois par connexion. */
    fun gardeProposee(installation: Installation): Boolean = coffre.lire(PREFIXE_PROPOSEE + cle(installation)) != null

    fun noterGardeProposee(installation: Installation) {
        coffre.ranger("1", PREFIXE_PROPOSEE + cle(installation))
    }

    /** Après une déconnexion : elle sera reproposée à la prochaine connexion. */
    fun oublierGardeProposee(installation: Installation) = coffre.effacer(PREFIXE_PROPOSEE + cle(installation))

    /**
     * Le statut et les rôles du compte dans cette résidence (#180), relus à chaque ouverture
     * (GET /auth/me) : la feuille « Résidences » les montre pour chacune, comme l'iOS
     * (`connues.noter(profil)`). Au coffre, avec ce qui dit qui est l'utilisateur.
     */
    fun noterRoles(installation: Installation, statut: String?, roles: List<String>) {
        coffre.ranger(json.encodeToString(RolesConnus.serializer(), RolesConnus(statut, roles)), PREFIXE_ROLES + cle(installation))
    }

    fun roles(installation: Installation): RolesConnus? =
        coffre.lire(PREFIXE_ROLES + cle(installation))?.let { runCatching { json.decodeFromString(RolesConnus.serializer(), it) }.getOrNull() }

    /** « Oublier toutes les résidences » : comme une app neuve. */
    fun vider() {
        for (installation in liste) {
            coffre.effacer(PREFIXE_ROLES + cle(installation))
            coffre.effacer(PREFIXE_IDENTIFIANT + cle(installation))
            oublierGardeProposee(installation)
        }
        liste = emptyList()
        enregistrer()
    }

    private fun cle(installation: Installation) = installation.adresse?.cleCoffre ?: installation.url

    private fun lireListe(): List<Installation> {
        val texte = stockage.lire() ?: return emptyList()
        // Un stockage illisible ne fait pas tomber l'app : on repart d'une liste vide.
        val lue = runCatching { json.decodeFromString(serialiseur, texte) }.getOrDefault(emptyList())
        // Une adresse qui ne se relit plus normalisée n'est pas gardée.
        return lue.filter { it.adresse != null }.distinctBy { it.url }
    }

    private fun enregistrer() = stockage.ecrire(json.encodeToString(serialiseur, liste))

    private companion object {
        const val PREFIXE_IDENTIFIANT = "identifiant."
        const val PREFIXE_PROPOSEE = "garde-proposee."
        const val PREFIXE_ROLES = "roles."
        val json = Json { ignoreUnknownKeys = true }
        val serialiseur = ListSerializer(Installation.serializer())
    }
}
