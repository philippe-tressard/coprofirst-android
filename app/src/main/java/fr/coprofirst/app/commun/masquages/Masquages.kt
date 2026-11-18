// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.masquages

import fr.coprofirst.api.modeles.MasquageCreate
import fr.coprofirst.api.routes.MasquagesApi
import fr.coprofirst.app.session.SessionOuverte
import java.time.Instant

/**
 * Un résident dont on masque ce qu'il publie, pour soi seul (`MasquageRead`, coprofirst#2861,
 * #282), comme l'iOS (`Masquage`). Rien n'est supprimé, et il n'en est pas averti ; le filtre
 * est au SERVEUR (idées, annonces, sondages, réponses, commentaires, suites et messages
 * d'affaire) — l'app ne le refait pas.
 */
data class Masquage(val auteurId: Int, val auteur: String, val creeLe: Instant)

/**
 * L'auteur qu'un geste propose de masquer : son identifiant et son nom, tel qu'affiché là où
 * le geste s'offre. Ce qui ne dit pas son auteur (une idée, un sondage) ne se masque pas.
 */
data class AuteurAMasquer(val id: Int, val nom: String) {
    companion object {
        /** `null` sans identifiant ni nom servis, ou pour soi : le geste ne s'offre pas. */
        fun de(id: Int?, nom: String?, moi: Int?): AuteurAMasquer? =
            if (id == null || nom.isNullOrBlank() || id == moi) null else AuteurAMasquer(id, nom)
    }
}

/**
 * GET, POST /masquages et DELETE /masquages/{auteur_id} — au-dessus du plancher (greffés,
 * `GREFFES`), appelés derrière la porte `VersionContrat.MODERATION`
 * (`Installation.sert`). Le serveur refuse en 422, avec sa phrase, ce qui ne se masque pas
 * (soi-même, le conseil syndical, l'administration, un modérateur qui masquerait) : l'app ne
 * redit pas la règle.
 */
interface ServiceMasquages {
    suspend fun masques(): List<Masquage>

    /** Idempotent, comme le serveur (201 sur un masque déjà posé). */
    suspend fun masquer(auteur: AuteurAMasquer)

    suspend fun demasquer(auteurId: Int)
}

/** Par le client GÉNÉRÉ, sur la session de la résidence. */
class ServiceMasquagesApi(private val session: SessionOuverte) : ServiceMasquages {
    override suspend fun masques() = session.appeler { b, c ->
        MasquagesApi(b, c).getMasquages().map { Masquage(it.auteurId, it.auteur, it.creeLe.toInstant()) }
    }

    override suspend fun masquer(auteur: AuteurAMasquer) {
        session.appeler { b, c -> MasquagesApi(b, c).postMasquages(MasquageCreate(auteurId = auteur.id)) }
    }

    override suspend fun demasquer(auteurId: Int) {
        session.appeler { b, c -> MasquagesApi(b, c).deleteMasquagesAuteurId(auteurId) }
    }
}
