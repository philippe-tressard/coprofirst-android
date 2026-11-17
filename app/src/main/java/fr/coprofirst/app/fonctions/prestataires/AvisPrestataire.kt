// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.prestataires

import fr.coprofirst.api.modeles.ExerciceInterventions
import fr.coprofirst.api.modeles.NotationRead
import fr.coprofirst.app.commun.LibelleTeinte
import java.time.Instant

/**
 * Un avis du conseil syndical sur un prestataire (`NotationRead`, contrat 2.0.0), comme
 * l'iOS (#207) : un ÉTAT (mauvais, neutre, excellent), son auteur, son commentaire, et le
 * droit SERVI de le corriger (`peut_modifier`) — lu, jamais recalculé.
 */
data class AvisPrestataire(
    val id: Int,
    val etat: String,
    val commentaire: String? = null,
    val auteurNom: String? = null,
    val creeLe: Instant,
    val peutModifier: Boolean = false,
    /** L'affaire jugée ; `null` : un avis sur le prestataire en général. */
    val ticketId: Int? = null,
    val ticketTitre: String? = null,
    val contratId: Int? = null,
)

/**
 * Un exercice du récapitulatif d'un prestataire (`ExerciceInterventions`) : ses
 * interventions, ses avis sans affaire, le compte des états — tout est SERVI par
 * `GET /prestataires/{id}/interventions`, l'app ne regroupe ni ne compte rien.
 */
data class ExerciceAvis(
    val annee: Int,
    val libelle: String,
    val nbInterventions: Int,
    val nbNotees: Int,
    /** Le compte des états, dans l'ordre servi ; seuls ceux qui en ont. */
    val comptes: List<Pair<String, Int>>,
    val lignes: List<LigneIntervention>,
)

/**
 * Une ligne d'un exercice : une affaire du prestataire et les avis portés sur ce couple
 * (aucun : « non notée ») ; ou, `generaux`, la ligne des avis sans affaire.
 */
data class LigneIntervention(
    val cle: String,
    val titre: String?,
    val date: Instant? = null,
    val avis: List<AvisPrestataire>,
) {
    val generaux: Boolean get() = cle == CLE_GENERAUX

    companion object {
        const val CLE_GENERAUX = "generaux"
    }
}

/**
 * L'état d'un avis ou d'une moyenne, en clair — la table GÉNÉRÉE du site (`TablesNotations`,
 * `notations.ts`, coprofirst#2775), comme l'iOS : l'app ne recopie ni libellé, ni teinte, ni
 * seuil. Un état inconnu se montre brut, en gris (`LibelleTeinte.brut`).
 */
object EtatAvis {
    fun teinte(etat: String): LibelleTeinte = TablesNotations.parEtat[etat] ?: LibelleTeinte.brut(etat)

    /** `etatDeMoyenne` du site : sous la première borne franchie, son état ; au-delà, le dernier. */
    fun deMoyenne(moyenne: Double): String =
        TablesNotations.seuilsDeMoyenne.firstOrNull { moyenne < it.first }?.second ?: TablesNotations.ETAT_AU_DELA
}

internal fun NotationRead.lu() = AvisPrestataire(
    id = id,
    etat = etat.orEmpty(),
    commentaire = commentaire?.takeIf { it.isNotBlank() },
    auteurNom = auteurNom,
    creeLe = creeLe.toInstant(),
    peutModifier = peutModifier ?: false,
    ticketId = ticketId,
    ticketTitre = ticketTitre,
    contratId = contratId,
)

internal fun ExerciceInterventions.lu(): ExerciceAvis {
    val lignes = interventions.orEmpty().map { i ->
        LigneIntervention("t${i.ticketId}", i.titre, i.date.toInstant(), i.avis.orEmpty().map { it.lu() })
    }
    val generaux = avisGeneraux.orEmpty().map { it.lu() }
    return ExerciceAvis(
        annee = annee,
        libelle = libelle,
        nbInterventions = nbInterventions ?: 0,
        nbNotees = nbNotees ?: 0,
        comptes = compte.orEmpty().filterValues { it > 0 }.toList(),
        lignes = if (generaux.isEmpty()) lignes else lignes + LigneIntervention(LigneIntervention.CLE_GENERAUX, null, null, generaux),
    )
}
