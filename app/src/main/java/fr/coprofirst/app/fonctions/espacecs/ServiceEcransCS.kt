// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.espacecs

import fr.coprofirst.api.modeles.Confirmation
import fr.coprofirst.api.modeles.EcranLu
import fr.coprofirst.api.routes.EcransApi
import fr.coprofirst.app.commun.lecture.LieuServi
import fr.coprofirst.app.commun.perimetres.ChoixDePerimetres
import fr.coprofirst.app.session.SessionOuverte
import java.time.Instant

/**
 * Ce que l'onglet « Écrans du hall » demande (#283, serveur coprofirst#2770), comme l'iOS
 * (`ServiceEcransCS`) : les gestes du CONSEIL — lister, confirmer le code qu'affiche un
 * écran, retirer. La part de l'écran lui-même (`/ecrans/appairage…`, `/ecrans/moi/…`) n'est
 * pas celle du téléphone.
 */
interface ServiceEcransCS {
    /** GET /ecrans : les écrans appairés, avec leur dernière lecture. */
    suspend fun ecrans(): List<EcranDuHall>

    /** POST /ecrans : le code court que l'écran affiche, son nom et son périmètre. */
    suspend fun appairer(saisie: SaisieAppairage): EcranAppaire

    /** DELETE /ecrans/{id} : son accès cesse aussitôt. */
    suspend fun retirer(id: Int)
}

class ServiceEcransCSApi(private val session: SessionOuverte) : ServiceEcransCS {
    override suspend fun ecrans() = session.appeler { b, c -> EcransApi(b, c).getEcrans().map(EcranDuHall::depuis) }

    override suspend fun appairer(saisie: SaisieAppairage) = session.appeler { b, c ->
        val corps = Confirmation(codeCourt = saisie.lettres, nom = saisie.nomSaisi, perimetre = saisie.perimetres.codes.single())
        EcransApi(b, c).postEcrans(corps).let { EcranAppaire(it.nom, it.perimetreLibelle) }
    }

    override suspend fun retirer(id: Int) {
        session.appeler { b, c -> EcransApi(b, c).deleteEcransEcranId(id) }
    }
}

/** Un écran, tel que le conseil le voit (`EcranLu`) — jamais son jeton. */
data class EcranDuHall(
    val id: Int,
    val nom: String,
    val perimetre: String,
    /** Le badge servi (`perimetre_libelle`) : vide pour le périmètre par défaut, qui se tait. */
    val perimetreLibelle: String,
    val creeLe: Instant,
    /** La dernière lecture des affiches — `null` tant qu'il n'a rien lu. */
    val vuLe: Instant?,
    val expireLe: Instant,
    /** Échu : il ne lit plus rien jusqu'à un nouvel appairage. */
    val echu: Boolean,
) {
    val lieu: String? get() = LieuServi.lieu(perimetreLibelle)

    companion object {
        fun depuis(e: EcranLu) = EcranDuHall(
            e.id, e.nom, e.perimetre, e.perimetreLibelle, e.creeLe.toInstant(), e.vuLe?.toInstant(), e.expireLe.toInstant(), e.echu,
        )
    }
}

/** Ce que deviendra l'écran (`AppairageConfirme`). */
data class EcranAppaire(val nom: String, val perimetreLibelle: String)

/** La saisie de « Appairer un écran » (`OngletEcrans.svelte`) : le code, le nom, UN périmètre. */
data class SaisieAppairage(
    /** Le code tel que l'écran l'affiche (`BCDF-GHJK`) : tiret, espaces et casse libres. */
    val code: String = "",
    val nom: String = "",
    val perimetres: ChoixDePerimetres = ChoixDePerimetres(unique = true),
) {
    /** Les huit lettres du code, comme le site les lit — le serveur normalise de nouveau, et refuse ce qui ne désigne aucune demande. */
    val lettres: String get() = code.uppercase().filter { it in 'A'..'Z' }

    val nomSaisi: String get() = nom.trim()

    /** La règle `complet` du site : huit lettres, un nom, un périmètre. */
    val complete: Boolean get() = lettres.length == LONGUEUR_CODE && nomSaisi.isNotEmpty() && perimetres.codes.size == 1

    companion object {
        /**
         * La longueur du code court (`appairage_ecran.LONGUEUR_CODE` du serveur), que le site attend aussi.
         * ⚠️ Divergence déclarée : elle n'est pas publiée avec la release (le contrat ne borne
         * `code_court` qu'à 32) ; écrite ici comme l'iOS, le serveur refuse un code faux de toute façon.
         */
        const val LONGUEUR_CODE = 8
    }
}
