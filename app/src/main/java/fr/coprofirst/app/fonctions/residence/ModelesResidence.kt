// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.residence

import fr.coprofirst.api.modeles.BatimentRead
import fr.coprofirst.api.modeles.CoproprieteRead
import fr.coprofirst.api.modeles.DiagnosticTypeRead
import fr.coprofirst.api.modeles.DocumentRead
import fr.coprofirst.api.modeles.EntreeCarnetLue
import fr.coprofirst.api.modeles.PerimetreRead
import fr.coprofirst.api.modeles.RegleResidenceLue
import fr.coprofirst.app.commun.formats.FormatsDuSite
import java.time.Instant

// Les modèles de « Ma résidence », comme l'iOS (`ModelesResidence`, `LectureResidence`) :
// chaque champ est celui que le serveur sert, l'app n'y calcule rien qu'il ne serve.

/** La fiche (GET /copropriete). `assuranceReconduit` et `syndicEchu` sont SERVIS. */
data class FicheResidence(
    val nom: String,
    val adresse: String,
    val photo: String? = null,
    val immatriculation: String? = null,
    val lotsPrincipaux: Int? = null,
    val lotsTotal: Int? = null,
    val parkingsCommuns: Int = 0,
    val assurance: String? = null,
    val assuranceEcheance: Instant? = null,
    val assuranceReconduit: Boolean = false,
    val syndic: String? = null,
    val syndicEcheance: Instant? = null,
    val syndicEchu: Boolean = false,
)

/** Un bâtiment et sa composition (GET /copropriete/batiments). */
data class Batiment(
    val id: Int,
    val numero: String,
    val parkings: Int,
    val caves: Int,
    val appartements: Int,
    val locauxCommerciaux: Int,
)

/** Un document (GET /documents?categorie=…) : plan, règlement, PV d'AG, ou une annexe. */
data class DocumentResidence(
    val id: Int,
    val titre: String,
    val fichier: String,
    val description: String? = null,
    val batimentId: Int? = null,
    val annee: Int? = null,
    /** La date de l'acte (`date_document`) ou de l'AG (`date_ag`) — jamais celle du dépôt. */
    val date: Instant? = null,
    val parent: Int? = null,
    val regle: Int? = null,
    val rapport: Int? = null,
)

/** Une règle ou recommandation ; `contenu` est du HTML de l'éditeur du site. */
data class RegleResidence(val id: Int, val titre: String, val contenu: String?)

/** Un diagnostic réglementaire et ses rapports (GET /diagnostics/types). */
data class TypeDiagnostic(
    val id: Int,
    val nom: String,
    val texteLegislatif: String,
    val frequence: String?,
    val nonApplicable: Boolean,
    val rapports: List<RapportDiagnostic>,
)

data class RapportDiagnostic(val id: Int, val titre: String, val fichier: String, val date: Instant?, val synthese: String?)

/** Tout ce que la Fiche lit, en une vague — comme le site (`chargerFiche`). */
data class ContenuFiche(
    val fiche: FicheResidence,
    val batiments: List<Batiment> = emptyList(),
    val regles: List<RegleResidence> = emptyList(),
    val plans: List<DocumentResidence> = emptyList(),
    val reglements: List<DocumentResidence> = emptyList(),
    val comptesRendus: List<DocumentResidence> = emptyList(),
    val diagnostics: List<TypeDiagnostic> = emptyList(),
    val annexes: List<DocumentResidence> = emptyList(),
) {
    fun annexesDeRegle(id: Int) = annexes.filter { it.regle == id }
    fun annexesDeDocument(id: Int) = annexes.filter { it.parent == id }
    fun annexesDeRapport(id: Int) = annexes.filter { it.rapport == id }

    /** Les types non applicables ne se montrent qu'au conseil syndical, sur le site. */
    val diagnosticsApplicables: List<TypeDiagnostic> get() = diagnostics.filter { !it.nonApplicable }

    /** Le numéro du bâtiment (« A ») que le site met dans « Bât. A » ; `null` : la copropriété. */
    fun numeroBatiment(id: Int?): NumeroBatiment = when (id) {
        null -> NumeroBatiment.Copropriete
        else -> batiments.firstOrNull { it.id == id }?.let { NumeroBatiment.Connu(it.numero) } ?: NumeroBatiment.Inconnu
    }

    /** La composition par bâtiment, puis les parkings communs ; chaque ligne : parkings, caves, appartements, commerces. */
    val composition: List<Pair<String?, List<Int>>>
        get() = batiments.map { it.numero to listOf(it.parkings, it.caves, it.appartements, it.locauxCommerciaux) } +
            (if (fiche.parkingsCommuns > 0) listOf(null to listOf(fiche.parkingsCommuns, 0, 0, 0)) else emptyList())

    /** Les totaux de chaque colonne de la composition. */
    val totaux: List<Int> get() = (0 until COLONNES).map { i -> composition.sumOf { it.second[i] } }

    companion object {
        const val COLONNES = 4
    }
}

/** Le bâtiment d'un plan : la copropriété, un bâtiment connu, ou un identifiant inconnu (« Bât. ? »). */
sealed interface NumeroBatiment {
    data object Copropriete : NumeroBatiment
    data object Inconnu : NumeroBatiment
    data class Connu(val numero: String) : NumeroBatiment
}

/** Une entrée du carnet d'entretien (`EntreeCarnetLue`). */
data class EntreeCarnet(
    val date: String,
    val libelle: String,
    val origine: String,
    val detail: String? = null,
    val numero: String? = null,
    val equipement: String? = null,
    val lien: String? = null,
    val alerte: String? = null,
    /** Un extrait du texte de l'affaire, en HTML. */
    val extrait: String? = null,
) {
    val cle: String get() = "$origine-${lien ?: libelle}-$date"

    /** L'identifiant de l'affaire d'une entrée « affaire » (`/tickets/29` → « 29 »), comme l'iOS. */
    val affaire: String?
        get() = lien?.takeIf { origine == "affaire" && it.startsWith(CHEMIN_AFFAIRE) }
            ?.removePrefix(CHEMIN_AFFAIRE)?.takeWhile { it.isDigit() }?.takeIf { it.isNotEmpty() }

    private companion object {
        const val CHEMIN_AFFAIRE = "/tickets/"
    }
    val jour: Instant? get() = FormatsDuSite.lire(date)
}

/** Un groupe du carnet : l'équipement (`null` : sans équipement rattaché) et ses entrées. */
data class GroupeCarnet(val equipement: String?, val titre: String?, val entrees: List<EntreeCarnet>)

/** Le carnet et les libellés des équipements qui rangent ses entrées. */
data class ContenuCarnet(val entrees: List<EntreeCarnet>, val libellesEquipements: Map<String, String> = emptyMap()) {
    /** Rangées par équipement, dans l'ordre servi : un groupe prend la place de sa première entrée. */
    val groupes: List<GroupeCarnet>
        get() = entrees.groupBy { it.equipement }.map { (code, liste) ->
            GroupeCarnet(code, code?.let { libellesEquipements[it] ?: it }, liste)
        }

    val enAlerte: List<EntreeCarnet> get() = entrees.filter { !it.alerte.isNullOrEmpty() }
}

/** Un périmètre ACTIF de la copropriété (GET /perimetres) : le filtre du carnet. */
data class Perimetre(val code: String, val libelle: String)

// Les réponses du client généré → les modèles. Aucune règle n'y est ajoutée.

private fun String?.nonVide(): String? = this?.takeIf { it.isNotEmpty() }
private fun java.time.LocalDate?.instant(): Instant? = this?.let { FormatsDuSite.lire(it.toString()) }

internal fun CoproprieteRead.lue() = FicheResidence(
    nom = nom, adresse = adresse, photo = photoUrl.nonVide(), immatriculation = numeroImmatriculation.nonVide(),
    lotsPrincipaux = nbLotsPrincipaux, lotsTotal = nbLotsTotal, parkingsCommuns = nbParkingsCommuns ?: 0,
    assurance = assuranceCompagnie.nonVide(), assuranceEcheance = assuranceEcheance.instant(),
    assuranceReconduit = assuranceReconduit == true, syndic = syndicCabinet.nonVide(),
    syndicEcheance = syndicEcheance.instant(), syndicEchu = syndicEchu == true,
)

internal fun BatimentRead.lu() = Batiment(id, numero, nbParkings ?: 0, nbCaves ?: 0, nbAppartements ?: 0, nbLocauxCommerciaux ?: 0)

internal fun DocumentRead.lu() = DocumentResidence(
    id = id, titre = titre, fichier = fichierNom, description = description.nonVide(), batimentId = batimentId,
    annee = annee, date = (dateAg ?: dateDocument).instant(), parent = documentParentId, regle = regleResidenceId,
    rapport = diagnosticRapportId,
)

internal fun RegleResidenceLue.lue() = RegleResidence(id, titre, contenu.nonVide())

internal fun DiagnosticTypeRead.lu() = TypeDiagnostic(
    id = id, nom = nom, texteLegislatif = texteLegislatif, frequence = frequence.nonVide(),
    nonApplicable = nonApplicable == true,
    rapports = rapports.orEmpty().map { RapportDiagnostic(it.id, it.titre, it.fichierNom, it.dateRapport.instant(), it.synthese.nonVide()) },
)

internal fun EntreeCarnetLue.lue() = EntreeCarnet(
    date = date, libelle = libelle, origine = origine.value, detail = detail.nonVide(), numero = numero.nonVide(),
    equipement = equipement.nonVide(), lien = lien.nonVide(), alerte = alerte.nonVide(), extrait = extrait.nonVide(),
)

/** L'arbre servi, ACTIFS seulement, comme l'iOS (`Perimetre.lus`). */
internal fun List<PerimetreRead>.actifs() = filter { it.actif }.map { Perimetre(it.code, it.libelle) }
