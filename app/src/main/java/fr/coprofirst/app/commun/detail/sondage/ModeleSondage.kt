// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.detail.sondage

import androidx.annotation.StringRes
import fr.coprofirst.api.modeles.OptionFiche
import fr.coprofirst.api.modeles.ReponseCommunauteRead
import fr.coprofirst.api.modeles.SondageDetail
import fr.coprofirst.api.modeles.VoteBody
import fr.coprofirst.app.R
import java.time.Instant
import kotlin.math.roundToInt

/**
 * Un sondage, tel que GET /sondages/{id} le sert (`SondageDetail`, typé par le contrat), et
 * l'identifiant du compte (GET /auth/me) : on ne signale pas son propre commentaire — comme
 * l'iOS (`DetailSondage`).
 */
data class DetailSondage(
    val id: Int,
    val question: String,
    /** Du HTML de l'éditeur du site : `TexteRiche`. */
    val description: String?,
    val options: List<OptionSondage>,
    val monVote: Int?,
    val cloture: Boolean,
    val clotureLe: Instant?,
    /** La décision du serveur (`resultats_visibles`) — jamais recomposée (#397 du site). */
    val resultatsVisibles: Boolean,
    val commentaires: List<ReponseCommunauteRead>,
    val moi: Int?,
) {
    /** `nb_votes` absent compte pour 0 : sans ce repli, la somme n'aurait pas de sens. */
    val totalVotes: Int get() = options.sumOf { it.nbVotes ?: 0 }

    /** Ouvert et pas encore voté : la règle de la fiche du site, sur les champs servis. */
    val peutVoter: Boolean get() = !cloture && monVote == null

    companion object {
        /** Un sondage tel que le contrat le type (`SondageDetail`). */
        fun depuis(lu: SondageDetail, moi: Int?) = DetailSondage(
            id = lu.id,
            question = lu.question,
            description = lu.description,
            options = lu.options.orEmpty().map(OptionSondage::depuis),
            monVote = lu.monVote,
            cloture = lu.cloture ?: false,
            clotureLe = lu.clotureLe?.toInstant(),
            resultatsVisibles = lu.resultatsVisibles ?: false,
            commentaires = lu.commentaires.orEmpty(),
            moi = moi,
        )
    }
}

/** Une réponse possible d'un sondage (`OptionFiche`). */
data class OptionSondage(
    val id: Int,
    val libelle: String,
    /** L'option demande une précision (« Précisez votre réponse »). */
    val champLibre: Boolean,
    /** Absent quand les résultats sont masqués — jamais 0 : « personne n'a voté » serait faux. */
    val nbVotes: Int?,
    /** Les précisions des votants, servies avec les résultats. */
    val reponsesLibres: List<String>,
) {
    companion object {
        fun depuis(lue: OptionFiche) =
            OptionSondage(lue.id, lue.libelle, lue.champLibre ?: false, lue.nbVotes, lue.reponsesLibres.orEmpty())
    }
}

/** Ce que POST /sondages/{id}/voter reçoit (`VoteBody`) : un texte vide n'est pas envoyé. */
data class VoteSondage(
    val option: Int? = null,
    val reponseLibre: String = "",
    val commentaire: String = "",
    val charteAcceptee: Boolean = false,
) {
    /** Choisir une réponse ; en changer vide la précision : elle valait pour l'autre (le site). */
    fun choisir(id: Int) = if (option == id) this else copy(option = id, reponseLibre = "")

    /** Ce qui manque encore, dans l'ordre où le site le dit ; `null` : le vote peut partir. */
    @StringRes
    fun manque(champLibre: Boolean): Int? = when {
        option == null -> R.string.sondage_choisir
        champLibre && nettoye(reponseLibre) == null -> R.string.sondage_preciser_manque
        !charteAcceptee -> R.string.sondage_charte_manque
        else -> null
    }

    /** Le corps de la requête ; `null` sans réponse choisie (le bouton l'attend). */
    fun corps(): VoteBody? = option?.let { VoteBody(optionId = it, commentaire = nettoye(commentaire), reponseLibre = nettoye(reponseLibre)) }

    companion object {
        fun nettoye(texte: String): String? = texte.trim().takeIf { it.isNotEmpty() }
    }
}

/** Les calculs de la fiche du site (`ResultatsSondage.svelte`). */
object CalculsSondage {
    /** Le pourcentage du site : arrondi, 0 sans aucun vote. */
    fun pourcentage(votes: Int, total: Int): Int = if (total == 0) 0 else (votes.toDouble() / total * 100).roundToInt()
}
