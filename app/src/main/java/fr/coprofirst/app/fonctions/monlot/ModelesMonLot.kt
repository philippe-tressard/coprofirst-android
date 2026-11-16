// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.monlot

import fr.coprofirst.api.modeles.AccesOut
import fr.coprofirst.api.modeles.AccesPorteur
import fr.coprofirst.api.modeles.BailLocataireOut
import fr.coprofirst.api.modeles.BailOut
import fr.coprofirst.api.modeles.LotRead
import fr.coprofirst.api.modeles.ObjetOut
import fr.coprofirst.app.commun.acces.TypeAcces
import fr.coprofirst.app.commun.formats.FormatsDuSite

/*
 * Ce que « Mes lots & accès » affiche, lu des types GÉNÉRÉS (`LotRead`, `AccesPorteur`,
 * `BailOut`…) par UN branchement chacun (`lu()`) — le même pour le service réel et pour
 * les réponses de test, comme l'iOS (`LotDuCompte.init(_:)`). La famille d'un accès est
 * commune : `TypeAcces` (commun/acces).
 */

/** Un lot du compte (`LotRead`, lu dans `/lots/mes-lots`). */
data class LotDuCompte(
    val id: Int,
    val numero: String,
    val type: String? = null,
    val typeAppartement: String? = null,
    val etage: Int? = null,
    val superficie: Double? = null,
    val batimentNom: String? = null,
    /** Mon lien au lot : `locataire` pour un lot que je LOUE. */
    val typeLien: String? = null,
    /** La porte ou la place, ce que le résident dit (« 5 & 6 »). */
    val numeroUsage: String? = null,
    val perimetres: List<PerimetreDuLot> = emptyList(),
    val alertes: List<ConstatDuLot> = emptyList(),
    /** « Appartement – T3 », « 2ème » : les libellés que l'API calcule. */
    val libelleTypeComplet: String? = null,
    val etageLibelle: String? = null,
) {
    val loue: Boolean get() = typeLien == "locataire"

    /** « Bât. A / 12 » — l'en-tête de la carte d'un lot sur le site. */
    val intitule: String get() = "${batimentNom ?: FormatsDuSite.TIRET} / $numero"

    /** Le type : le libellé servi (`libelle_type_complet`), sinon la donnée brute — `lotTypeLabel` n'est jamais recopié. */
    val typeLu: String
        get() = libelleTypeComplet?.takeIf { it.isNotEmpty() }
            ?: listOfNotNull(type, typeAppartement).filter { it.isNotEmpty() }.joinToString(" ").ifEmpty { FormatsDuSite.TIRET }

    /** L'étage : le libellé servi (`etage_libelle`), sinon le nombre brut — `etageLabel` n'est jamais recopié. */
    val etageLu: String? get() = etageLibelle?.takeIf { it.isNotEmpty() } ?: etage?.toString()

    /** Les périmètres d'une relation (`situe_dans`, `desservi_par`), réunis ; `null` s'il n'y en a pas. */
    fun perimetres(relation: String): String? =
        perimetres.filter { it.relation == relation }.map { it.libelle }.takeIf { it.isNotEmpty() }?.joinToString(", ")
}

data class PerimetreDuLot(val libelle: String, val relation: String)

/** Ce que le propriétaire doit savoir de son lot (`Constat`). */
data class ConstatDuLot(val code: String, val gravite: String, val message: String)

/** Un badge ou une télécommande (`AccesPorteur` ou `AccesOut`). */
data class AccesDetenu(
    val id: Int,
    val code: String,
    val statut: String,
    /** `vigik` ou `telecommande` — celui de la ROUTE qui l'a servi (`AccesPorteur` ne le porte pas), ou celui d'`AccesOut`. */
    val type: String,
    val chezLocataire: Boolean = false,
    val bailId: Int? = null,
)

/** Un bail (`BailOut`). */
data class Bail(
    val id: Int,
    val lotId: Int,
    val statut: String,
    val locataireId: Int? = null,
    val locatairePrenom: String? = null,
    val locataireNom: String? = null,
    val locataireEmail: String? = null,
    val locataireTelephone: String? = null,
    val dateEntree: String,
    val dateSortiePrevue: String? = null,
    val dateSortieReelle: String? = null,
    val notes: String? = null,
    val objets: List<ObjetRemis> = emptyList(),
    /** `locataire_nom_affiche` servi. */
    val locataireNomServi: String? = null,
) {
    /** `bailEnCours`, `front/src/lib/bail.ts`. */
    val enCours: Boolean get() = statut == "actif" || statut == "en_cours_sortie"
    val termine: Boolean get() = statut == "termine"
    val nomLocataire: String get() = FormatsDuSite.nom(locataireNomServi, locatairePrenom, locataireNom)

    /** Le regroupement du site : un même locataire, ou ce bail seul (`ext_<id>`). */
    val cleLocataire: String get() = locataireId?.toString() ?: "ext_$id"
}

/** Un objet remis au locataire (`ObjetOut`). */
data class ObjetRemis(
    val id: Int,
    val type: String,
    val libelle: String,
    val quantite: Int,
    val reference: String?,
    val statut: String,
    val remisLe: String?,
    val renduLe: String?,
)

/** Le bail vu du locataire (`BailLocataireOut`). */
data class BailDuLocataire(
    val id: Int,
    val statut: String,
    val lot: LotDuCompte,
    val dateEntree: String,
    val dateSortiePrevue: String?,
    val bailleurEmail: String?,
    val bailleurTelephone: String?,
    /** `bailleur_nom_affiche` servi, sinon prénom et nom. */
    val nomBailleur: String,
)

/** Une demande d'accès (`CommandeAccesCreate`). */
data class DemandeAcces(
    val type: TypeAcces = TypeAcces.Vigik,
    val lotId: Int? = null,
    val quantite: Int = 1,
    val motif: String = "",
)

// ── Lus des types générés : le MÊME branchement pour le service réel et les tests ──

fun LotRead.lu() = LotDuCompte(
    id = id, numero = numero, type = type, typeAppartement = typeAppartement, etage = etage,
    superficie = superficie?.toDouble(), batimentNom = batimentNom, typeLien = typeLien, numeroUsage = numeroUsage,
    perimetres = perimetres.orEmpty().map { PerimetreDuLot(it.libelle, it.relation) },
    alertes = alertes.orEmpty().map { ConstatDuLot(it.code, it.gravite, it.message) },
    libelleTypeComplet = libelleTypeComplet, etageLibelle = etageLibelle,
)

/** Un accès DÉTENU (`/acces/mes-vigiks`, `/acces/mes-telecommandes`) : son type est celui de la route. */
fun AccesPorteur.lu(type: TypeAcces) =
    AccesDetenu(id, code, statut.value, type.code, chezLocataire, bailId)

/** Un accès CONFIÉ par un bailleur (`AccesOut`, qui porte son type). */
fun AccesOut.lu() = AccesDetenu(id, code, statut.value, type, chezLocataire, bailId)

fun BailOut.lu() = Bail(
    id = id, lotId = lotId, statut = statut.value, locataireId = locataireId, locatairePrenom = locatairePrenom,
    locataireNom = locataireNom, locataireEmail = locataireEmail, locataireTelephone = locataireTelephone,
    dateEntree = dateEntree.toString(), dateSortiePrevue = dateSortiePrevue?.toString(),
    dateSortieReelle = dateSortieReelle?.toString(), notes = notes, objets = objets.orEmpty().map { it.lu() },
    locataireNomServi = locataireNomAffiche,
)

fun ObjetOut.lu() = ObjetRemis(
    id, type.value, libelle, quantite, reference, statut.value, remisLe?.toString(), renduLe?.toString(),
)

/** Le bail servi : son lot se lit comme un lot du compte (l'étage en clair compris). */
fun BailLocataireOut.lu() = BailDuLocataire(
    id = id,
    statut = statut.value,
    lot = LotDuCompte(
        id = lotId, numero = lotNumero ?: FormatsDuSite.TIRET, type = lotType, typeAppartement = lotTypeAppartement,
        etage = lotEtage, superficie = lotSuperficie?.toDouble(), batimentNom = lotBatimentNom,
        libelleTypeComplet = lotLibelleTypeComplet, etageLibelle = lotEtageLibelle,
    ),
    dateEntree = dateEntree.toString(),
    dateSortiePrevue = dateSortiePrevue?.toString(),
    bailleurEmail = bailleurEmail,
    bailleurTelephone = bailleurTelephone,
    nomBailleur = FormatsDuSite.nom(bailleurNomAffiche, bailleurPrenom, bailleurNom),
)
