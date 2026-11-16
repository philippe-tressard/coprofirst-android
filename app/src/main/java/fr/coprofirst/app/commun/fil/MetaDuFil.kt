// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.fil

import fr.coprofirst.app.commun.formats.FormatsDuSite
import fr.coprofirst.app.commun.lecture.Lecture
import fr.coprofirst.app.commun.lecture.LieuServi
import fr.coprofirst.app.commun.pieces.PiecesJointes
import fr.coprofirst.app.commun.texte.Separateur
import java.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Ce que l'app relit de `meta` d'un élément du fil — un dictionnaire LIBRE du contrat
 * (`FluxItem.meta`, les routeurs `routers/flux` de coprofirst), comme l'iOS (`MetaDuFil`,
 * `init(item:)`) : ce que désigne l'élément (affaire, sondage, document), l'urgence d'une
 * actualité, et la ligne de pastilles d'une affaire (`meta.affaire`) — son échéance, ses
 * natures, qui la lit et son lieu —, puis ses pièces jointes. Une clé absente ou d'un autre
 * type vaut « rien », jamais une erreur : la carte garde le reste.
 */
data class MetaDuFil(
    val ticketId: Int? = null,
    val sondageId: Int? = null,
    val documentId: Int? = null,
    val fichierNom: String? = null,
    /** `meta.urgente` d'une actualité, et son `meta.statut`. */
    val urgente: Boolean = false,
    val statut: String? = null,
    val echeance: EcheanceDuFil? = null,
    /** Les natures que le serveur DÉRIVE (`utils/nature_affaire`) — jamais redérivées ici. */
    val natures: List<String> = emptyList(),
    /** « Qui la lit » (`meta.affaire.lecture`, #129) ; `null` hors affaire, ou illisible. */
    val lecture: Lecture? = null,
    /** Le lieu de l'affaire (`perimetre_libelle`, `LieuServi`) ; `null` pour le périmètre par défaut. */
    val lieu: String? = null,
    val pieces: PiecesJointes = PiecesJointes(),
) {
    companion object {
        fun lire(meta: Map<String, JsonElement>?): MetaDuFil {
            meta ?: return MetaDuFil()
            val affaire = meta["affaire"] as? JsonObject
            return MetaDuFil(
                ticketId = meta.entier("ticket_id"),
                sondageId = meta.entier("sondage_id"),
                documentId = meta.entier("document_id"),
                fichierNom = meta.texte("fichier_nom"),
                urgente = (meta["urgente"] as? JsonPrimitive)?.booleanOrNull ?: false,
                statut = meta.texte("statut"),
                echeance = affaire?.let(EcheanceDuFil::lire),
                natures = affaire?.textes("natures").orEmpty(),
                lecture = Lecture.lire(affaire?.get("lecture")),
                lieu = LieuServi.lieu(affaire?.texte("perimetre_libelle")),
                pieces = PiecesJointes(meta.textes("photos_urls") + meta.textes("fichiers_urls")),
            )
        }
    }
}

/**
 * L'échéance d'une affaire DATÉE du fil, comme l'iOS (`EcheanceDuFil`, #75) : le délai, puis
 * la ou les dates — la vignette de la gouttière du site (`EcheanceGouttiere.svelte`).
 *
 * Le délai n'est PAS compté ici : le serveur le dérive en jours civils de Paris et sert son
 * libellé ; l'app l'affiche tel quel. Elle ne formate que les deux dates (`FormatsDuSite`).
 * Une installation qui ne sert ni `echeance` ni `debut` : pas d'échéance.
 */
data class EcheanceDuFil(
    /** « J-8 », « Jour J », « En cours », « Passé » — tel que servi ; `null` pour une affaire close. */
    val libelle: String?,
    /** `etat == "passee"` : le délai perd la teinte de la nature. */
    val passee: Boolean,
    /** Le début, tel que servi (ISO), lu comme le site le lit. */
    val debut: String,
    /** La fin, seulement quand elle tombe un autre jour (`plusieurs_jours`). */
    val fin: String?,
) {
    /** « mar. 17 nov. », puis « → 19 nov. » sur plusieurs jours. */
    fun dates(maintenant: Instant = Instant.now()): String {
        val premier = FormatsDuSite.jourCourt(debut, maintenant = maintenant)
        return fin?.let { premier + Separateur.fleche + FormatsDuSite.jourCourt(it, semaine = false, maintenant = maintenant) } ?: premier
    }

    companion object {
        /** La règle de la vignette du site : rien sans échéance ni début ; la fin, seulement sur plusieurs jours. */
        fun lire(affaire: JsonObject): EcheanceDuFil? {
            val echeance = affaire["echeance"] as? JsonObject ?: return null
            val debut = affaire.texte("debut")?.takeIf { it.isNotEmpty() } ?: return null
            val plusieursJours = (echeance["plusieurs_jours"] as? JsonPrimitive)?.booleanOrNull == true
            return EcheanceDuFil(
                libelle = echeance.texte("libelle"),
                passee = echeance.texte("etat") == "passee",
                debut = debut,
                fin = if (plusieursJours) affaire.texte("fin") else null,
            )
        }
    }
}

/** Une chaîne servie, ou `null` (absente, nulle, d'un autre type). */
private fun Map<String, JsonElement>.texte(cle: String): String? =
    (this[cle] as? JsonPrimitive)?.takeIf { it.isString }?.content

/** Une liste de chaînes servie ; ce qui n'en est pas une est ignoré. */
private fun Map<String, JsonElement>.textes(cle: String): List<String> =
    (this[cle] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }

/** Un entier servi — un nombre JSON peut arriver entier ou flottant, comme l'iOS le lit. */
private fun Map<String, JsonElement>.entier(cle: String): Int? {
    val valeur = (this[cle] as? JsonPrimitive)?.takeUnless { it.isString } ?: return null
    return valeur.intOrNull ?: valeur.doubleOrNull?.toInt()
}
