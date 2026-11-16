// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.residence

import fr.coprofirst.api.modeles.ExtraitRead
import fr.coprofirst.api.modeles.QuestionReglementRead
import fr.coprofirst.app.commun.texte.Separateur
import java.time.Instant

/** L'état de l'assistant du règlement (GET /reglement). */
data class EtatReglement(val disponible: Boolean, val texteDisponible: Boolean)

/** Une question posée à l'assistant, et son avis (`QuestionReglementRead`), comme l'iOS. */
data class QuestionReglement(
    val id: Int,
    val question: String,
    val reponse: String,
    /** La réponse sans ses « [R2] », servie (contrat 1.14.0) : l'aperçu de la carte la lit. */
    val reponseSansRenvois: String,
    val verdictLibelle: String,
    /** Ce que la capsule du verdict signale (`verdict_ton`) — `succes`, `alerte`… */
    val verdictTon: String,
    val poseeLe: Instant,
    val texteEnVigueur: Boolean = true,
    val selonReglement: String? = null,
    val selonAG: String? = null,
    val synthese: String? = null,
    val reserves: String? = null,
    val provenance: String? = null,
    val modele: String? = null,
    val auteur: String? = null,
    val dansLaFAQ: Boolean = false,
    val extraits: List<ExtraitReglement> = emptyList(),
    /** Les renvois que l'avis cite sans extrait derrière (servis) : l'avis le dit. */
    val renvoisSansExtrait: List<String> = emptyList(),
) {
    val extraitsReglement: List<ExtraitReglement> get() = extraits.filter { it.source != SOURCE_AG }
    val extraitsAG: List<ExtraitReglement> get() = extraits.filter { it.source == SOURCE_AG }
    val verifies: Int get() = extraits.count { it.verifie }

    /** Un avis à RENVOIS se lit en synthèse, fondements et sections pliées ; un avis d'avant, section après section. */
    val avecRenvois: Boolean get() = extraits.any { it.renvoi != null }

    /** « Ce qui fonde l'avis » : le règlement d'abord, puis les AG, comme le site. */
    val fondements: List<ExtraitReglement> get() = extraitsReglement + extraitsAG

    /** La classe de la capsule du verdict, pour le ton servi (`BadgeVerdict` du site ; `TablesReglement`, générée). */
    val badgeDuVerdict: String get() = TablesReglement.badgesDuVerdict[verdictTon] ?: TablesReglement.BADGE_INCONNU

    companion object {
        /** La source d'un extrait tiré d'une décision d'assemblée générale. */
        const val SOURCE_AG = "ag"
    }
}

data class ExtraitReglement(
    val citation: String,
    val verifie: Boolean,
    val reference: String? = null,
    val apport: String? = null,
    val acte: String? = null,
    /** Le chapitre, la section ou la résolution, déduit du texte par le serveur. */
    val rubrique: String? = null,
    val page: String? = null,
    val source: String? = null,
    /** Le renvoi que l'avis cite (`R1`, `AG2`) — absent d'un avis d'avant. */
    val renvoi: String? = null,
) {
    /** L'acte, la rubrique et la page, joints comme le site les joint, ou la référence donnée à défaut. */
    val origine: String?
        get() {
            val morceaux = listOfNotNull(acte, rubrique, page).filter { it.isNotEmpty() }
            return if (morceaux.isEmpty()) reference?.takeIf { it.isNotEmpty() } else morceaux.joinToString(Separateur.tiret)
        }
}

internal fun ExtraitRead.lu() = ExtraitReglement(
    citation = citation, verifie = verifie, reference = reference?.takeIf { it.isNotEmpty() },
    apport = apport?.takeIf { it.isNotEmpty() }, acte = acte, rubrique = rubrique, page = page, source = source,
    renvoi = renvoi,
)

internal fun QuestionReglementRead.lue() = QuestionReglement(
    id = id, question = question, reponse = reponse, reponseSansRenvois = reponseSansRenvois,
    verdictLibelle = verdictLibelle, verdictTon = verdictTon.value, poseeLe = creeLe.toInstant(),
    texteEnVigueur = texteEnVigueur, selonReglement = selonReglement, selonAG = selonAg, synthese = synthese,
    reserves = reserves, provenance = provenance, modele = modele, auteur = auteurNom, dansLaFAQ = faqItemId != null,
    extraits = extraits.map { it.lu() }, renvoisSansExtrait = renvoisSansExtrait,
)
