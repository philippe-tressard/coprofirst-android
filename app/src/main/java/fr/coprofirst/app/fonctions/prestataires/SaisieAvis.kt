// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.prestataires

import fr.coprofirst.api.modeles.AffaireAvis
import fr.coprofirst.api.modeles.NotationCreate
import fr.coprofirst.api.modeles.NotationUpdate

/**
 * La saisie d'un avis (`GesteNoter.svelte`, `FormulaireNotation.svelte`, #209), comme
 * l'`AvisSaisi` de l'iOS : l'état, le commentaire, l'affaire jugée et la confirmation de
 * l'ajout du prestataire à cette affaire. Pure, testée sur la JVM.
 *
 * ⚠️ Manque déclaré : les points forts et faibles à cocher, et la phrase qu'ils composent
 * (`POINTS_FORTS`, `phraseGeneree`, `decomposerCommentaire` de `notations.ts`), ne sont pas
 * publiés dans `tables-clients.json` (coprofirst#2775). L'app ne les recopie pas : le
 * commentaire est libre en entier, et un avis composé sur le site revient tel quel dans la
 * zone de texte, sans rien perdre.
 */
data class SaisieAvis(
    val etat: String? = null,
    val commentaire: String = "",
    /** L'affaire jugée ; `null` : un avis sur le prestataire en général. */
    val affaire: Int? = null,
    val confirmeAjout: Boolean = false,
) {
    /** Ce qui retient l'avis (les refus du site), ou `null` quand il peut partir. */
    fun refus(ajoutRequis: Boolean): RefusAvis? = when {
        etat == null -> RefusAvis.EtatManquant
        etat == ETAT_MAUVAIS && commentaire.isBlank() -> RefusAvis.MauvaisSansCommentaire
        ajoutRequis && !confirmeAjout -> RefusAvis.AjoutNonConfirme
        else -> null
    }

    /** POST /prestataires/notations : l'avis, rattaché au contrat s'il vient de sa carte. */
    fun creation(prestataire: Int, contrat: Int?, ajoutRequis: Boolean) = NotationCreate(
        prestataireId = prestataire,
        etat = NotationCreate.Etat.entries.first { it.value == etat },
        commentaire = commentaire.trim().ifEmpty { null },
        contratId = contrat,
        ticketId = affaire,
        confirmerAjout = ajoutRequis && confirmeAjout,
    )

    /**
     * PATCH /prestataires/notations/{n_id} : SEULEMENT ce qui change (#99). Un commentaire
     * vidé part en `""`. Une affaire retirée doit partir en `"ticket_id": null` explicite,
     * que le client n'écrit pas seul : `effacerAffaire` le dit au service (`NulsExplicites`).
     */
    fun correction(avant: AvisPrestataire, ajoutRequis: Boolean): CorrectionAvis {
        val texte = commentaire.trim()
        return CorrectionAvis(
            NotationUpdate(
                etat = etat?.takeIf { it != avant.etat }?.let { e -> NotationUpdate.Etat.entries.first { it.value == e } },
                commentaire = texte.takeIf { it != avant.commentaire.orEmpty() },
                ticketId = affaire?.takeIf { it != avant.ticketId },
                confirmerAjout = ajoutRequis && confirmeAjout,
            ),
            effacerAffaire = affaire == null && avant.ticketId != null,
        )
    }

    companion object {
        /** Corriger son avis : la saisie part de ce qu'il dit. */
        fun corrigeant(avis: AvisPrestataire) =
            SaisieAvis(etat = avis.etat.ifEmpty { null }, commentaire = avis.commentaire.orEmpty(), affaire = avis.ticketId)

        /** Les états d'un avis, dans l'ordre du site (`TablesNotations.etats`, #229). */
        val etats: List<String> = TablesNotations.etats

        /** Le seul état qui exige un commentaire, côté site et serveur. */
        const val ETAT_MAUVAIS = "mauvais"
    }
}

/** Les refus du site, avant tout envoi. */
enum class RefusAvis { EtatManquant, MauvaisSansCommentaire, AjoutNonConfirme }

/** Une correction : le corps partiel, et s'il faut effacer l'affaire (`"ticket_id": null`). */
data class CorrectionAvis(val corps: NotationUpdate, val effacerAffaire: Boolean)

/** Une affaire qu'un avis peut juger (`AffaireAvis`) ; `intervient` : le prestataire y figure déjà. */
data class AffaireANoter(val id: Int, val numero: String, val titre: String, val intervient: Boolean)

internal fun AffaireAvis.lu() = AffaireANoter(id, numero, titre, intervient)

/**
 * Ce que montre le choix de l'affaire (`ChoixAffaireAvis.svelte`), comme l'iOS. Deux
 * groupes, dans cet ordre : les affaires où le prestataire intervient, puis les autres
 * (servies déjà triées). Une frappe filtre sur le numéro et le titre, et la recherche
 * DÉTAILLÉE de la page Affaires (`GET /tickets/recherche`) ajoute ce qu'elle trouve, à son
 * rang. Aucune seconde règle de recherche ici.
 */
object ChoixAffaire {
    /** Sans recherche, les plus récentes de chaque groupe ; au-delà, on cherche. */
    const val PAR_GROUPE = 6
    private const val PAR_GROUPE_TROUVEES = 20

    /** `termeAffaire` : sans le « # » que l'écran écrit devant un numéro. */
    fun terme(recherche: String): String = recherche.trim().removePrefix("#").trim()

    /**
     * Les deux groupes : (celles du prestataire, les autres). `trouvees` : les identifiants
     * de la recherche détaillée, dans son ordre de pertinence. `minimum` : la longueur à
     * partir de laquelle une frappe est une recherche.
     */
    fun groupes(
        affaires: List<AffaireANoter>,
        recherche: String,
        trouvees: List<Int>,
        minimum: Int,
    ): Pair<List<AffaireANoter>, List<AffaireANoter>> {
        val cherche = terme(recherche).lowercase()
        val active = cherche.length >= minimum
        val rang = trouvees.withIndex().associate { (i, id) -> id to i }
        val retenues = if (!active) {
            affaires
        } else {
            affaires.filter { "${it.numero} ${it.titre}".lowercase().contains(cherche) || it.id in rang }
                .sortedWith(compareBy<AffaireANoter> { !it.intervient }.thenBy { rang[it.id] ?: -1 })
        }
        val borne = if (active) PAR_GROUPE_TROUVEES else PAR_GROUPE
        return retenues.filter { it.intervient }.take(borne) to retenues.filterNot { it.intervient }.take(borne)
    }
}
